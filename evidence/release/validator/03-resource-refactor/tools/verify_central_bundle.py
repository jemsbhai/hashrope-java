"""Verify a Central Portal release bundle without accessing publishing credentials."""
import hashlib
import io
from pathlib import Path, PurePosixPath
import struct
import xml.etree.ElementTree as ET
import zipfile
import argparse
import base64
import binascii
import json
import re
import subprocess
import tempfile

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
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise ValueError('duplicate archive entry')
            if len(names) > 4096 or sum(item.file_size for item in archive.infolist()) > MAX_BYTES:
                raise ValueError('archive exceeds inspection size limit')
            for name in names:
                path = PurePosixPath(name)
                if '\\' in name or path.is_absolute() or '..' in path.parts or ':' in name:
                    raise ValueError('unsafe archive path')
            return {item.filename: archive.read(item) for item in archive.infolist() if not item.is_dir()}
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


def _public_packets(armor):
    """Reject secret-key packets before GPG imports this public-only input."""
    if len(armor)>1024*1024:
        raise ValueError('public key exceeds inspection size limit')
    try:
        lines=armor.decode('ascii').strip().splitlines()
    except UnicodeDecodeError as error:
        raise ValueError('expected ASCII armored public key') from error
    if not lines or lines[0]!='-----BEGIN PGP PUBLIC KEY BLOCK-----' or lines[-1]!='-----END PGP PUBLIC KEY BLOCK-----':
        raise ValueError('expected one armored public key block')
    encoded=[]
    for line in lines[1:-1]:
        if not line or line.startswith(('Version:','Comment:','=')):
            continue
        encoded.append(line)
    try:
        data=base64.b64decode(''.join(encoded),validate=True)
    except (ValueError,binascii.Error) as error:
        raise ValueError('invalid public key armor') from error
    position=0
    if not data:
        raise ValueError('public key has no packets')
    while position<len(data):
        header=data[position];position+=1
        if not header&0x80:
            raise ValueError('invalid public key packet')
        tag=(header&0x3f) if header&0x40 else ((header>>2)&15)
        if tag in (5,7):
            raise ValueError('secret-key packet is forbidden')
        if header&0x40:
            if position>=len(data): raise ValueError('truncated public key packet')
            first=data[position];position+=1
            if first<192:
                length=first
            elif first<224:
                if position>=len(data): raise ValueError('truncated public key packet')
                length=((first-192)<<8)+data[position]+192;position+=1
            elif first==255:
                if position+4>len(data): raise ValueError('truncated public key packet')
                length=int.from_bytes(data[position:position+4],'big');position+=4
            else:
                raise ValueError('partial public key packet length is unsupported')
        else:
            length_type=header&3
            if length_type==3:
                length=len(data)-position
            else:
                width=1<<length_type
                if position+width>len(data): raise ValueError('truncated public key packet')
                length=int.from_bytes(data[position:position+width],'big');position+=width
        if position+length>len(data):
            raise ValueError('truncated public key packet')
        position+=length


class PublicKeyVerifier:
    """Use GPG with a fresh public-only keyring; never consult the user's keyring."""
    def __init__(self,public_key,fingerprint,gpg='gpg',runner=subprocess.run):
        self.armor=Path(public_key).read_bytes()
        _public_packets(self.armor)
        self.fingerprint=fingerprint.upper()
        if not re.fullmatch('[0-9A-F]{40}',self.fingerprint):
            raise ValueError('expected a full 40-digit signing fingerprint')
        self.gpg,self.runner=gpg,runner
        self.temporary=None
        self.home=None

    def _run(self,*arguments):
        command=[self.gpg,'--no-options','--homedir',str(self.home),'--batch','--no-tty','--no-auto-key-retrieve','--no-autostart','--status-fd','1',*map(str,arguments)]
        try:
            result=self.runner(command,capture_output=True,text=True,timeout=30,check=False)
        except (OSError,subprocess.SubprocessError) as error:
            raise ValueError('GPG verification process could not complete') from error
        if result.returncode:
            raise ValueError('GPG verification process failed; no raw diagnostics retained')
        return result.stdout

    def __enter__(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='hashrope-public-key-')
        self.home=Path(self.temporary.name)
        try:
            key=self.home/'public.asc';key.write_bytes(self.armor)
            self._run('--import',key)
            listing=self._run('--with-colons','--fingerprint','--list-keys')
            primary=[];expect_fingerprint=False
            for line in listing.splitlines():
                fields=line.split(':')
                if fields[0] in ('sec','ssb'):
                    raise ValueError('secret-key data appeared in public keyring')
                if fields[0]=='pub':
                    expect_fingerprint=True
                elif fields[0]=='sub':
                    expect_fingerprint=False
                elif fields[0]=='fpr' and expect_fingerprint:
                    primary.append(fields[9].upper());expect_fingerprint=False
            if len(primary)!=1:
                raise ValueError('public keyring must contain exactly one primary key')
            if primary[0]!=self.fingerprint:
                raise ValueError('public key primary fingerprint mismatch')
            return self
        except BaseException:
            self.__exit__(None,None,None)
            raise

    def __call__(self,data,signature):
        if self.home is None:
            raise ValueError('public key verifier must be used as a context manager')
        artifact=self.home/'artifact.bin';armor=self.home/'artifact.bin.asc'
        artifact.write_bytes(data);armor.write_bytes(signature)
        status=self._run('--verify',armor,artifact)
        valid=[]
        bad={'BADSIG','ERRSIG','EXPSIG','EXPKEYSIG','REVKEYSIG','KEYEXPIRED','SIGEXPIRED','NODATA','FAILURE','ERROR'}
        for line in status.splitlines():
            fields=line.split()
            if len(fields)<2 or fields[0]!='[GNUPG:]':
                continue
            if fields[1] in bad:
                raise ValueError('detached signature rejected by GPG status')
            if fields[1]=='VALIDSIG':
                if len(fields)<11:
                    raise ValueError('malformed valid-signature status')
                valid.append((fields[11] if len(fields)>11 else fields[2]).upper())
        if valid!=[self.fingerprint]:
            raise ValueError('detached signature must verify against the exact primary fingerprint')
        return self.fingerprint

    def __exit__(self,*unused):
        if self.temporary is not None:
            self.temporary.cleanup()
        self.home=None
        return False


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path,required=True)
    parser.add_argument('--public-key',type=Path,required=True)
    parser.add_argument('--fingerprint',required=True)
    parser.add_argument('--gpg',default='gpg')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    if args.output and args.output.exists():
        parser.error('output already exists; choose a new evidence file')
    try:
        with PublicKeyVerifier(args.public_key,args.fingerprint,args.gpg) as signature:
            report=verify_bundle(args.bundle,signature)
    except (ValueError,OSError) as error:
        parser.exit(1,'Bundle verification failed: '+str(error)+'\n')
    encoded=json.dumps(report,indent=2)+'\n'
    if args.output:
        with args.output.open('x',encoding='utf-8') as output:
            output.write(encoded)
    print(encoded,end='')


if __name__=='__main__':
    main()
