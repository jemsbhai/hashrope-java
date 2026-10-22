"""Verify a Central Portal release bundle without accessing publishing credentials."""
import hashlib
import io
from pathlib import Path, PurePosixPath
import struct
import xml.etree.ElementTree as ET
import zipfile

COORDINATES = 'io.github.jemsbhai:hashrope:0.2.2'
PREFIX = 'io/github/jemsbhai/hashrope/0.2.2/'
BASE = 'hashrope-0.2.2'
ARTIFACTS = (BASE+'.pom', BASE+'.jar', BASE+'-sources.jar', BASE+'-javadoc.jar')
ALGORITHMS = ('md5', 'sha1', 'sha256', 'sha512')
API = ('Hashrope', 'Node', 'Leaf', 'Internal', 'RepeatNode', 'Rope', 'SlidingWindow', 'PolynomialHash')
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
MAX_BYTES = 64 * 1024 * 1024


def _archive(data):
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate archive entry')
        if len(names) > 4096 or sum(item.file_size for item in archive.infolist()) > MAX_BYTES:
            raise ValueError('archive exceeds inspection size limit')
        for name in names:
            path = PurePosixPath(name)
            if '\\' in name or path.is_absolute() or '..' in path.parts or ':' in name:
                raise ValueError('unsafe archive path')
        result = {item.filename: archive.read(item) for item in archive.infolist() if not item.is_dir()}
        archive.close()
        return result
    except (zipfile.BadZipFile, RuntimeError, OSError) as error:
        raise ValueError('invalid ZIP archive') from error


def _pom(data):
    if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError('POM metadata must not contain XML entities')
    try:
        root = ET.fromstring(data)
    except ET.ParseError as error:
        raise ValueError('invalid POM metadata XML') from error
    def value(path):
        return (root.findtext(path, default='', namespaces=NS) or '').strip()
    if ':'.join(value('m:'+field) for field in ('groupId','artifactId','version')) != COORDINATES:
        raise ValueError('unexpected POM coordinates')
    if value('m:packaging') not in ('', 'jar'):
        raise ValueError('unexpected POM packaging')
    required = ('m:name','m:description','m:url','m:licenses/m:license/m:name','m:licenses/m:license/m:url','m:developers/m:developer/m:name','m:scm/m:url','m:scm/m:connection','m:scm/m:developerConnection')
    for path in required:
        if not value(path):
            raise ValueError('missing POM metadata: '+path)
    if value('m:licenses/m:license/m:name').upper() != 'MIT':
        raise ValueError('POM metadata must identify MIT license')
    for dependency in root.findall('m:dependencies/m:dependency', NS):
        scope = (dependency.findtext('m:scope', default='compile', namespaces=NS) or '').strip()
        if scope != 'test':
            raise ValueError('unexpected runtime or non-test dependency')


def _jar(name, data):
    entries = _archive(data)
    license_names = ('META-INF/LICENSE','META-INF/LICENSE.txt','LICENSE','LICENSE.txt')
    licenses = [entries[p] for p in license_names if p in entries]
    if not any(all(marker in content for marker in (b'MIT License', b'Copyright', b'Permission is hereby granted', b'THE SOFTWARE IS PROVIDED')) for content in licenses):
        raise ValueError('missing MIT license text in '+name)
    suffix = '.class' if name == BASE+'.jar' else '.java' if name.endswith('-sources.jar') else '.html'
    required = {'hashrope/'+name+suffix for name in API}
    if suffix == '.html':
        required.add('index.html')
    if not required.issubset(entries):
        raise ValueError('missing API entries in '+name)
    if suffix == '.class':
        for path, content in entries.items():
            if not path.endswith('.class'):
                continue
            if not path.startswith('hashrope/') or 'Test' in path:
                raise ValueError('unexpected class in main JAR')
            if len(content) < 8 or content[:4] != b'\xca\xfe\xba\xbe' or struct.unpack('>H',content[6:8])[0] != 61:
                raise ValueError('class must use Java 17 bytecode')


def verify_bundle(bundle, verify_signature):
    """Inspect the archive, then call verify_signature(data, armor) for each artifact."""
    raw = Path(bundle).read_bytes()
    if len(raw) > MAX_BYTES:
        raise ValueError('bundle exceeds inspection size limit')
    entries = _archive(raw)
    required = {PREFIX+name+suffix for name in ARTIFACTS for suffix in ('','.asc',*( '.'+x for x in ALGORITHMS))}
    optional = {PREFIX+name+'.asc.'+algorithm for name in ARTIFACTS for algorithm in ALGORITHMS}
    missing, unexpected = required-set(entries), set(entries)-required-optional
    if missing:
        raise ValueError('missing required bundle files: '+', '.join(sorted(missing)))
    if unexpected:
        raise ValueError('unexpected bundle files: '+', '.join(sorted(unexpected)))
    for name, checksum in entries.items():
        algorithm = name.rsplit('.',1)[-1]
        if algorithm in ALGORITHMS:
            payload = entries[name.rsplit('.',1)[0]]
            if checksum.strip().lower() != hashlib.new(algorithm,payload).hexdigest().encode('ascii'):
                raise ValueError('checksum mismatch: '+name)
    _pom(entries[PREFIX+ARTIFACTS[0]])
    for name in ARTIFACTS[1:]:
        _jar(name, entries[PREFIX+name])
    report = {'coordinates':COORDINATES, 'bundle_sha256':hashlib.sha256(raw).hexdigest(), 'entry_count':len(entries), 'artifacts':[]}
    for name in ARTIFACTS:
        data, signature = entries[PREFIX+name], entries[PREFIX+name+'.asc']
        if not signature.startswith(b'-----BEGIN PGP SIGNATURE-----') or b'-----END PGP SIGNATURE-----' not in signature:
            raise ValueError('detached signature must be ASCII armored')
        fingerprint = verify_signature(data, signature)
        report['artifacts'].append({'name':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'signer_primary_fingerprint':fingerprint,'signature_verified':True})
    report['verified'] = True
    return report
