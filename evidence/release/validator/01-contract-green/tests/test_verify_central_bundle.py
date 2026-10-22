import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('verify_central_bundle', ROOT/'tools/verify_central_bundle.py')
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)

FINGERPRINT = '3C8AD6A286A2FB052BF081B29D6C450FD82C949C'
PREFIX = 'io/github/jemsbhai/hashrope/0.2.2/'
NAMES = ('hashrope-0.2.2.pom', 'hashrope-0.2.2.jar', 'hashrope-0.2.2-sources.jar', 'hashrope-0.2.2-javadoc.jar')
LICENSE = b'MIT License\nCopyright (c) 2026 Muntaser Syed\nPermission is hereby granted, free of charge, to any person obtaining a copy\nTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND\n'
POM = b'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>io.github.jemsbhai</groupId><artifactId>hashrope</artifactId><version>0.2.2</version><name>Hashrope</name><description>Polynomial hash ropes</description><url>https://github.com/jemsbhai/hashrope-java</url><licenses><license><name>MIT</name><url>https://opensource.org/licenses/MIT</url></license></licenses><developers><developer><id>jemsbhai</id><name>Muntaser Syed</name></developer></developers><scm><url>https://github.com/jemsbhai/hashrope-java</url><connection>scm:git:https://github.com/jemsbhai/hashrope-java.git</connection><developerConnection>scm:git:ssh://git@github.com/jemsbhai/hashrope-java.git</developerConnection></scm><dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency></dependencies></project>'''


def jar(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, 'w') as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return result.getvalue()


def payloads():
    classes = {f'hashrope/{name}.class': b'\xca\xfe\xba\xbe'+struct.pack('>HH', 0, 61) for name in ('Hashrope','Node','Leaf','Internal','RepeatNode','Rope','SlidingWindow','PolynomialHash')}
    sources = {f'hashrope/{name}.java': f'package hashrope; class {name} {{}}'.encode() for name in ('Hashrope','Node','Leaf','Internal','RepeatNode','Rope','SlidingWindow','PolynomialHash')}
    docs = {f'hashrope/{name}.html': b'<html>API documentation</html>' for name in ('Hashrope','Node','Leaf','Internal','RepeatNode','Rope','SlidingWindow','PolynomialHash')}
    docs['index.html'] = b'<html>Hashrope API</html>'
    for entries in (classes, sources, docs):
        entries['META-INF/LICENSE'] = LICENSE
    return dict(zip(NAMES, (POM, jar(classes), jar(sources), jar(docs))))


def bundle_entries(data=None):
    entries = {}
    for name, content in (data or payloads()).items():
        entries[PREFIX+name] = content
        entries[PREFIX+name+'.asc'] = b'-----BEGIN PGP SIGNATURE-----\nfixture\n-----END PGP SIGNATURE-----\n'
        for algorithm in ('md5','sha1','sha256','sha512'):
            entries[PREFIX+name+'.'+algorithm] = hashlib.new(algorithm, content).hexdigest().encode()
    return entries


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name)/'bundle.zip'
        self.calls = []

    def verify(self, entries=None):
        self.path.write_bytes(jar(entries if entries is not None else bundle_entries()))
        def signature(data, signature):
            self.calls.append((data, signature))
            return FINGERPRINT
        return VERIFIER.verify_bundle(self.path, signature)

    def test_valid_bundle_reports_only_public_metadata_and_checks_all_signatures(self):
        report = self.verify()
        self.assertEqual('io.github.jemsbhai:hashrope:0.2.2', report['coordinates'])
        self.assertEqual(4, len(self.calls))
        self.assertEqual(4, len(report['artifacts']))
        self.assertEqual(24, report['entry_count'])
        self.assertTrue(report['verified'])
        self.assertNotIn('fixture', json.dumps(report))

    def test_missing_required_files_fail(self):
        for suffix in ('','.asc','.md5','.sha1','.sha256','.sha512'):
            with self.subTest(suffix=suffix):
                entries=bundle_entries(); del entries[PREFIX+NAMES[0]+suffix]
                with self.assertRaisesRegex(ValueError, 'missing'):
                    self.verify(entries)

    def test_bad_checksums_fail_before_signature_execution(self):
        for algorithm in ('md5','sha1','sha256','sha512'):
            entries=bundle_entries(); entries[PREFIX+NAMES[0]+'.'+algorithm]=b'0'
            with self.subTest(algorithm=algorithm), self.assertRaisesRegex(ValueError,'checksum'):
                self.verify(entries)
        self.assertEqual([], self.calls)

    def test_unknown_and_metadata_files_are_rejected(self):
        for name in ('maven-metadata-local.xml','_remote.repositories','private-key.asc','../escape','/absolute'):
            entries=bundle_entries(); entries[PREFIX+name]=b'unexpected'
            with self.subTest(name=name), self.assertRaisesRegex(ValueError,'unexpected|unsafe'):
                self.verify(entries)

    def test_duplicate_archive_entries_fail(self):
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w') as archive:
            for name,data in bundle_entries().items(): archive.writestr(name,data)
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter('ignore'); archive.writestr(PREFIX+NAMES[0],POM)
        self.path.write_bytes(buffer.getvalue())
        with self.assertRaisesRegex(ValueError,'duplicate'):
            VERIFIER.verify_bundle(self.path, lambda *_:FINGERPRINT)

    def test_optional_signature_checksums_are_verified(self):
        entries=bundle_entries(); name=PREFIX+NAMES[0]+'.asc'
        entries[name+'.sha256']=hashlib.sha256(entries[name]).hexdigest().encode()
        self.assertTrue(self.verify(entries)['verified'])
        entries[name+'.sha256']=b'bad'
        with self.assertRaisesRegex(ValueError,'checksum'): self.verify(entries)

    def test_coordinates_are_exact(self):
        data=payloads(); data[NAMES[0]]=POM.replace(b'<version>0.2.2</version>',b'<version>0.2.3</version>')
        with self.assertRaisesRegex(ValueError,'coordinates'): self.verify(bundle_entries(data))

    def test_required_pom_metadata_is_not_blank(self):
        for before,after in ((b'<description>Polynomial hash ropes</description>',b'<description/>'),(b'<name>MIT</name>',b'<name/>'),(b'<name>Muntaser Syed</name>',b'<name/>'),(b'<connection>scm:git:https://github.com/jemsbhai/hashrope-java.git</connection>',b'<connection/>')):
            data=payloads(); data[NAMES[0]]=POM.replace(before,after)
            with self.subTest(before=before), self.assertRaisesRegex(ValueError,'metadata'): self.verify(bundle_entries(data))

    def test_runtime_dependencies_are_rejected(self):
        for scope in (b'',b'<scope>runtime</scope>',b'<scope>compile</scope>'):
            data=payloads(); data[NAMES[0]]=POM.replace(b'<scope>test</scope>',scope)
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError,'runtime'): self.verify(bundle_entries(data))

    def test_all_jars_need_mit_license_text(self):
        for name in NAMES[1:]:
            data=payloads()
            with zipfile.ZipFile(io.BytesIO(data[name])) as archive:
                altered={p:archive.read(p) for p in archive.namelist() if p!='META-INF/LICENSE'}
            data[name]=jar(altered)
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'license'): self.verify(bundle_entries(data))

    def test_all_required_api_classes_sources_and_documentation_exist(self):
        for name,entry in ((NAMES[1],'hashrope/Rope.class'),(NAMES[2],'hashrope/Rope.java'),(NAMES[3],'hashrope/Rope.html')):
            data=payloads()
            with zipfile.ZipFile(io.BytesIO(data[name])) as archive:
                altered={p:archive.read(p) for p in archive.namelist() if p!=entry}
            data[name]=jar(altered)
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'missing'): self.verify(bundle_entries(data))

    def test_main_jar_rejects_test_foreign_or_non_java17_classes(self):
        for name,value in (('hashrope/RopeTest.class',b'\xca\xfe\xba\xbe\0\0\0='),('foreign/Surprise.class',b'\xca\xfe\xba\xbe\0\0\0='),('hashrope/Rope.class',b'\xca\xfe\xba\xbe\0\0\0D')):
            data=payloads()
            with zipfile.ZipFile(io.BytesIO(data[NAMES[1]])) as archive:
                altered={p:archive.read(p) for p in archive.namelist()}
            altered[name]=value;data[NAMES[1]]=jar(altered)
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,'class'): self.verify(bundle_entries(data))


if __name__=='__main__':
    unittest.main()
