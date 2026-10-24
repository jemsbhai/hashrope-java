"""Retain completed public verification evidence without changing artifacts or caches."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RUN = HERE/'run-0.3.4'
DEST = ROOT/'evidence/release-0.3.4/public-central'
SOURCE = '82e771bdae1f6293f7328bc4ee488e6317abe8c0'
MERGE = 'aa0708905ee629654a4d38654146cf3a8b1d7b42'
FINGERPRINT = '3C8AD6A286A2FB052BF081B29D6C450FD82C949C'
sha = lambda data: hashlib.sha256(data).hexdigest()

def write_json(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')

def retain(path, name=None):
    name = name or path.name
    with (DEST/name).open('xb') as stream:
        stream.write(path.read_bytes())

result = json.loads((RUN/'verification.json').read_text())
assert result['verified'] and result['public_artifacts'] == 24
assert result['consumer'] == {'tests': 3, 'failures': 0, 'errors': 0, 'skipped': 0}
assert result['script_sha256'] == sha((HERE/'verify_public.py').read_bytes())
assert (HERE/'verify_public.py').read_bytes() == (DEST/'verify_public.py').read_bytes()
bundle = ROOT/'target/release-0.3.4-frozen/central-bundle.zip'
assert sha(bundle.read_bytes()) == result['signed_bundle_sha256'] and bundle.stat().st_size == 210486
with zipfile.ZipFile(bundle) as archive:
    expected = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
with zipfile.ZipFile(RUN/'public-bundle.zip') as public:
    assert set(public.namelist()) == set(expected)
    for name, data in expected.items():
        assert public.read(name) == data == (RUN/'downloads'/Path(name).name).read_bytes()
signatures = json.loads((RUN/'public-bundle-verification.json').read_text())
assert signatures['verified'] and signatures['entry_count'] == 24 and len(signatures['artifacts']) == 4
assert all(a['signature_verified'] and a['signer_primary_fingerprint'] == FINGERPRINT for a in signatures['artifacts'])
validation = json.loads((ROOT/'evidence/release-0.3.4/validation.json').read_text())
assert all(sha((ROOT/name).read_bytes()) == value for name, value in validation['tested_sources'].items())
assert subprocess.check_output(['git', 'rev-parse', 'v0.3.4^{commit}'], cwd=ROOT, text=True).strip() == SOURCE
consumer_files = [RUN/'consumer/pom.xml'] + [p for p in (RUN/'consumer/src').rglob('*') if p.is_file()]
consumer_hashes = {}
for path in consumer_files:
    name = 'consumer-smoke/'+path.relative_to(RUN/'consumer').as_posix()
    assert path.read_bytes() == (ROOT/name).read_bytes() == subprocess.check_output(['git', 'show', SOURCE+':'+name], cwd=ROOT)
    consumer_hashes[name] = sha(path.read_bytes())
provenance = []
origins = {'central-only', 'central-only-'+hashlib.sha1(b'https://repo.maven.apache.org/maven2').hexdigest()}
for path in sorted((RUN/'m2').rglob('_remote.repositories')):
    entries = []
    for line in path.read_text(encoding='utf-8').splitlines():
        if line and not line.startswith('#'):
            name, origin = line.rsplit('>', 1)
            assert origin.endswith('=') and origin[:-1] in origins
            entries.append({'artifact': name, 'repository_id': origin[:-1]})
    provenance.append({'path': path.relative_to(RUN/'m2').as_posix(), 'entries': entries})
assert len(provenance) == result['repository_provenance_files'] == 230
write_json(RUN/'remote-provenance.json', provenance)
reports = []
for path in sorted((RUN/'consumer/target/surefire-reports').glob('TEST-*.xml')):
    suite = ET.parse(path).getroot()
    reports.append({'file': path.name, 'sha256': sha(path.read_bytes()), 'counts': {k: int(suite.get(k, '0')) for k in ('tests', 'failures', 'errors', 'skipped')}, 'testcases': [{'class': t.get('classname'), 'name': t.get('name'), 'time': t.get('time')} for t in suite.findall('testcase')]})
assert sum(r['counts']['tests'] for r in reports) == 3
write_json(RUN/'consumer-results.json', {'source_files': consumer_hashes, 'reports': reports})
for name in ('central-only-settings.xml', 'commands.json', 'empty-settings.xml', 'maven-0.log', 'maven-1.log', 'maven-2.log', 'public-artifact-comparison.json', 'public-bundle-verification.json', 'verification.json', 'remote-provenance.json', 'consumer-results.json'):
    retain(RUN/name)
for name in ('availability.jsonl', 'execution.log', 'probe_availability.py', 'retain_evidence.py'):
    retain(HERE/name)
files = {p.name: sha(p.read_bytes()) for p in sorted(DEST.iterdir()) if p.is_file() and p.name != 'README.md'}
write_json(DEST/'publication.json', {
    'recorded_at_utc': datetime.now(timezone.utc).isoformat(),
    'coordinates': 'io.github.jemsbhai:hashrope:0.3.4',
    'deployment_id': '52121508-fc68-459e-ac68-1cf275959923',
    'deployment_name': 'hashrope-0.3.4-parity-release',
    'portal_observed_by_release_coordinator': {'state': 'PUBLISHED', 'at_utc': '2026-10-24T21:58:38Z', 'prior_states': ['VALIDATING', 'PUBLISHING'], 'coordinator_submitted_or_resubmitted': False, 'publishing_mode': 'Unknown; observed state transitions do not identify whether a user click or automatic mode initiated publishing.'},
    'public_availability_verified': True,
    'public_repository_url': 'https://repo.maven.apache.org/maven2/io/github/jemsbhai/hashrope/0.3.4/',
    'reviewed_source_commit': SOURCE,
    'source_pr_merge_commit': MERGE,
    'release_tag': 'v0.3.4', 'release_tag_commit': SOURCE,
    'tested_source_hashes_unchanged': True,
    'signed_bundle_bytes': 210486, 'signed_bundle_sha256': result['signed_bundle_sha256'],
    'public_files_identical_to_signed_bundle': 24, 'public_signatures_verified': 4,
    'consumer': result['consumer'], 'verification_completed_at_utc': result['verified_at_utc'],
    'environment': validation['environment'],
    'history_note': 'upload-preparation.json is the unchanged pre-upload snapshot. Its source_commit field names the source PR merge; reviewed source and merge are distinguished above. Its false upload/publication fields describe that earlier snapshot, not the final state.',
    'archive_note': 'The reconstructed public ZIP has different ZIP metadata and its own hash. All 24 entry bytes, including signatures and checksum files, are identical to the frozen uploaded bundle.',
    'evidence_files': files,
})
print('Retained public evidence: 24 identical files, four signatures, three fresh consumer tests, 230 Central-only provenance files.')
