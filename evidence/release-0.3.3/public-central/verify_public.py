"""Public-only verification of the signed Java 0.3.3 release. Never publishes."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_BUNDLE_SHA256 = '2566cbcbb7aec38a9f7f9d5c351187dba02efcfd9345c03bb985bbce5282fb01'
PREFIX = 'io/github/jemsbhai/hashrope/0.3.3/'
CENTRAL = 'https://repo.maven.apache.org/maven2/'
FINGERPRINT = '3C8AD6A286A2FB052BF081B29D6C450FD82C949C'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def run(command, directory, log):
    with log.open('x', encoding='utf-8', newline='\n') as stream:
        result = subprocess.run(command, cwd=directory, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}); see {log}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing outputs are never overwritten')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    bundle_path = ROOT/'java/target/central-publishing/central-bundle.zip'
    bundle_data = bundle_path.read_bytes()
    assert sha(bundle_data) == EXPECTED_BUNDLE_SHA256, 'Signed bundle identity changed'
    with zipfile.ZipFile(bundle_path) as bundle:
        expected = {name: bundle.read(name) for name in bundle.namelist() if not name.endswith('/')}
    assert len(expected) == 24 and all(name.startswith(PREFIX) for name in expected)
    spec = importlib.util.spec_from_file_location('release_verifier', ROOT/'tools/verify_central_bundle.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    downloads = output/'downloads'
    downloads.mkdir()
    downloaded = {}
    comparisons = []
    try:
        for name, expected_data in expected.items():
            url = CENTRAL+name
            request = urllib.request.Request(url, headers={'User-Agent': 'Hashrope-public-release-verification/0.3.3'})
            with urllib.request.urlopen(request, timeout=60) as response:
                assert response.status == 200 and response.url == url, 'Unexpected public response/redirect'
                data = response.read(64*1024*1024+1)
            assert len(data) <= 64*1024*1024, 'Unexpected artifact size'
            assert data == expected_data, 'Public bytes differ from signed bundle: '+name
            (downloads/Path(name).name).write_bytes(data)
            downloaded[name] = data
            comparisons.append({'url': url, 'http_status': 200, 'bytes': len(data), 'sha256': sha(data), 'identical_to_signed_bundle': True})
    finally:
        write_json(output/'public-artifact-comparison.json', {'completed': len(comparisons)==24, 'bundle_sha256': EXPECTED_BUNDLE_SHA256, 'files': comparisons})
    public_bundle = output/'public-bundle.zip'
    with zipfile.ZipFile(public_bundle, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in downloaded.items():
            archive.writestr(name, data)
    with verifier.PublicKeyVerifier(ROOT/'evidence/release/public-signing-key.asc', FINGERPRINT) as signatures:
        verification = verifier.verify_bundle(public_bundle, signatures)
    write_json(output/'public-bundle-verification.json', verification)

    # A new project and empty cache prevent the locally installed release from satisfying resolution.
    consumer = output/'consumer'
    consumer.mkdir()
    shutil.copyfile(ROOT/'consumer-smoke/pom.xml', consumer/'pom.xml')
    shutil.copytree(ROOT/'consumer-smoke/src', consumer/'src')
    local_repo = output/'m2'
    local_repo.mkdir()
    assert not any(local_repo.iterdir())
    settings = output/'central-only-settings.xml'
    shutil.copyfile(ROOT/'evidence/release/central-only-settings.xml', settings)
    empty_settings = output/'empty-settings.xml'
    shutil.copyfile(ROOT/'tools/isolated-settings.xml', empty_settings)
    maven = shutil.which('mvn.cmd') or shutil.which('mvn')
    assert maven, 'Maven executable unavailable'
    common = [maven, '-B', '-ntp', '-s', str(settings), '-gs', str(empty_settings), '-Dmaven.repo.local='+str(local_repo), '-f', str(consumer/'pom.xml')]
    commands = [common+['clean', 'verify']]
    commands += [common+['org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get', '-Dartifact=io.github.jemsbhai:hashrope:0.3.3:jar:'+classifier, '-Dtransitive=false'] for classifier in ('sources', 'javadoc')]
    write_json(output/'commands.json', commands)
    for i, command in enumerate(commands):
        run(command, output, output/f'maven-{i}.log')
    tests = failures = errors = skipped = 0
    for report in (consumer/'target/surefire-reports').glob('TEST-*.xml'):
        suite = ET.parse(report).getroot()
        tests += int(suite.get('tests', '0'))
        failures += int(suite.get('failures', '0'))
        errors += int(suite.get('errors', '0'))
        skipped += int(suite.get('skipped', '0'))
    assert (tests, failures, errors, skipped) == (3, 0, 0, 0), 'Consumer results did not pass expected tests'
    cached = []
    for suffix in ('.pom', '.jar', '-sources.jar', '-javadoc.jar'):
        name = 'hashrope-0.3.3'+suffix
        data = (local_repo/PREFIX/name).read_bytes()
        assert data == expected[PREFIX+name], 'Maven-resolved bytes differ: '+name
        cached.append({'name': name, 'sha256': sha(data), 'identical_to_signed_bundle': True})
    provenance_files = list(local_repo.rglob('_remote.repositories'))
    assert provenance_files, 'No Maven repository provenance found'
    for provenance in provenance_files:
        for line in provenance.read_text(encoding='utf-8').splitlines():
            if line and not line.startswith('#'):
                assert line.endswith('>central-only='), 'Unexpected repository provenance: '+str(provenance)
    library_provenance = (local_repo/PREFIX/'_remote.repositories').read_text(encoding='utf-8')
    for artifact in cached:
        assert artifact['name']+'>central-only=' in library_provenance
    result = {'verified_at_utc': datetime.now(timezone.utc).isoformat(), 'coordinates': 'io.github.jemsbhai:hashrope:0.3.3', 'public_artifacts': 24, 'signed_bundle_sha256': EXPECTED_BUNDLE_SHA256, 'script_sha256': sha(Path(__file__).read_bytes()), 'public_signatures_verified': True, 'fresh_local_repository': str(local_repo), 'all_repository_provenance_central_only': True, 'repository_provenance_files': len(provenance_files), 'consumer': {'tests': tests, 'failures': failures, 'errors': errors, 'skipped': skipped}, 'maven_resolved_artifacts': cached, 'verified': True}
    write_json(output/'verification.json', result)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
