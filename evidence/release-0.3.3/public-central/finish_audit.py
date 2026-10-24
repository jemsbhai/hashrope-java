"""Audit existing successful Central resolution with Maven 3.10 provenance IDs."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
RUN = Path(__file__).resolve().parent/'run-0.3.3'
URL = 'https://repo.maven.apache.org/maven2'
ID = 'central-only'
PREFIX = 'io/github/jemsbhai/hashrope/0.3.3/'
BUNDLE_SHA = '2566cbcbb7aec38a9f7f9d5c351187dba02efcfd9345c03bb985bbce5282fb01'
sha = lambda data: hashlib.sha256(data).hexdigest()

settings = ET.parse(RUN/'central-only-settings.xml').getroot()
ns = {'m': 'http://maven.apache.org/SETTINGS/1.2.0'}
assert settings.find('m:servers', ns) is None
mirrors = settings.findall('m:mirrors/m:mirror', ns)
assert len(mirrors) == 1
assert mirrors[0].findtext('m:id', namespaces=ns) == ID
assert mirrors[0].findtext('m:url', namespaces=ns) == URL
assert mirrors[0].findtext('m:mirrorOf', namespaces=ns) == '*'
empty = ET.parse(RUN/'empty-settings.xml').getroot()
assert len(empty) == 0
bundle_path = ROOT/'java/target/central-publishing/central-bundle.zip'
assert sha(bundle_path.read_bytes()) == BUNDLE_SHA
with zipfile.ZipFile(bundle_path) as bundle:
    expected = {name: bundle.read(name) for name in bundle.namelist() if not name.endswith('/')}
assert len(expected) == 24
with zipfile.ZipFile(RUN/'public-bundle.zip') as public:
    assert set(public.namelist()) == set(expected)
    for name, data in expected.items():
        assert public.read(name) == data
        assert (RUN/'downloads'/Path(name).name).read_bytes() == data
public_report = json.loads((RUN/'public-bundle-verification.json').read_text())
assert public_report['verified'] and public_report['entry_count'] == 24
assert all(a['signature_verified'] and a['signer_primary_fingerprint']=='3C8AD6A286A2FB052BF081B29D6C450FD82C949C' for a in public_report['artifacts'])
counts = {k: 0 for k in ('tests','failures','errors','skipped')}
for p in (RUN/'consumer/target/surefire-reports').glob('TEST-*.xml'):
    suite = ET.parse(p).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, '0'))
assert counts == {'tests':3,'failures':0,'errors':0,'skipped':0}
for i in range(3):
    assert '[INFO] BUILD SUCCESS' in (RUN/f'maven-{i}.log').read_text(encoding='utf-8')
repo = RUN/'m2'
hashed_id = ID+'-'+hashlib.sha1(URL.encode()).hexdigest()
expected_ids = {ID, hashed_id}
provenance = []
for path in repo.rglob('_remote.repositories'):
    entries = []
    for line in path.read_text(encoding='utf-8').splitlines():
        if line and not line.startswith('#'):
            artifact, origin = line.rsplit('>',1)
            assert origin.endswith('=') and origin[:-1] in expected_ids, (path,line)
            entries.append({'artifact':artifact,'repository_id':origin[:-1]})
    provenance.append({'path':path.relative_to(repo).as_posix(),'entries':entries})
assert provenance
cached = []
for suffix in ('.pom','.jar','-sources.jar','-javadoc.jar'):
    name = 'hashrope-0.3.3'+suffix
    data = (repo/PREFIX/name).read_bytes()
    assert data == expected[PREFIX+name]
    marker = (repo/PREFIX/'_remote.repositories').read_text()
    assert any(name+'>'+origin+'=' in marker for origin in expected_ids)
    cached.append({'name':name,'sha256':sha(data),'identical_to_signed_bundle':True})
for p in (RUN/'consumer/src').rglob('*'):
    if p.is_file():
        assert p.read_bytes() == (ROOT/'consumer-smoke/src'/p.relative_to(RUN/'consumer/src')).read_bytes()
assert (RUN/'consumer/pom.xml').read_bytes() == (ROOT/'consumer-smoke/pom.xml').read_bytes()
commands = json.loads((RUN/'commands.json').read_text())
assert len(commands) == 3
assert commands[0][-2:] == ['clean','verify']
for command in commands:
    assert '-Dmaven.repo.local='+str(repo) in command
    assert command[command.index('-s')+1] == str(RUN/'central-only-settings.xml')
    assert command[command.index('-gs')+1] == str(RUN/'empty-settings.xml')
    assert not any(arg in ('install','deploy') for arg in command)
result = {'verified_at_utc':datetime.now(timezone.utc).isoformat(),'coordinates':'io.github.jemsbhai:hashrope:0.3.3','signed_bundle_sha256':BUNDLE_SHA,'public_artifacts_identical':24,'public_signatures_verified':4,'consumer':counts,'fresh_local_repository':str(repo),'credentials_free_central_only_mirror':URL,'repository_id':hashed_id,'repository_url_sha1':hashlib.sha1(URL.encode()).hexdigest(),'repository_provenance_files':len(provenance),'repository_provenance_entries':sum(len(p['entries']) for p in provenance),'maven_resolved_artifacts':cached,'original_script_sha256':sha((RUN.parent/'verify_public.py').read_bytes()),'post_run_audit_sha256':sha(Path(__file__).read_bytes()),'parser_correction':'Original final audit expected an unsuffixed repository ID; Maven 3.10 records the ID plus SHA1 of the repository URL. Existing successful resolution was audited against exactly this derived ID, without rerunning Maven or changing its cache.','verified':True}
for name,value in (('remote-provenance.json',provenance),('verification.json',result)):
    with (RUN/name).open('x',encoding='utf-8',newline='\n') as stream:
        json.dump(value,stream,indent=2)
        stream.write('\n')
print(json.dumps(result,indent=2))
