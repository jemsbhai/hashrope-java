"""Release packaging contracts; run after Maven package/install/deploy."""
import unittest
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}

class ReleasePackagingTest(unittest.TestCase):
    def test_archives_carry_exact_project_license(self):
        license_bytes = (ROOT/'LICENSE').read_bytes()
        for classifier in ('', '-sources', '-javadoc'):
            with self.subTest(classifier=classifier):
                with zipfile.ZipFile(ROOT/f'java/target/hashrope-0.3.3{classifier}.jar') as jar:
                    self.assertIn('META-INF/LICENSE', jar.namelist(), 'Distribution must carry the MIT notice')
                    self.assertEqual(license_bytes, jar.read('META-INF/LICENSE'))

    def test_release_profile_supports_maven_310_and_offline_review(self):
        pom=ET.parse(ROOT/'java/pom.xml').getroot()
        profile=pom.find("m:profiles/m:profile[m:id='central-release']",NS)
        self.assertIsNotNone(profile,'An opt-in Central Portal release profile is required')
        publisher=profile.find("m:build/m:plugins/m:plugin[m:artifactId='central-publishing-maven-plugin']",NS)
        self.assertIsNotNone(publisher,'Use the supported Portal publisher')
        self.assertEqual('0.12.0',publisher.findtext('m:version',namespaces=NS),'Maven 3.10 needs the bundle bookkeeping fix')
        self.assertEqual('true',publisher.findtext('m:extensions',namespaces=NS))
        self.assertEqual('${central.skipUpload}',publisher.findtext('m:configuration/m:skipUpload',namespaces=NS))
        self.assertEqual('true',profile.findtext('m:properties/m:central.skipUpload',namespaces=NS),'Reviewable bundle is default; upload must be explicit')
        signer=profile.find("m:build/m:plugins/m:plugin[m:artifactId='maven-gpg-plugin']",NS)
        self.assertIsNotNone(signer,'The POM and every attached artifact must be signed')
        self.assertEqual('true',signer.findtext('m:configuration/m:bestPractices',namespaces=NS))
        self.assertIsNone(signer.find('m:configuration/m:passphrase',NS),'Never store a passphrase in the POM')

    def test_pom_has_complete_public_metadata_without_runtime_dependencies(self):
        pom=ET.parse(ROOT/'java/pom.xml').getroot()
        for xpath in ('groupId','artifactId','version','name','description','url','licenses/license/name','licenses/license/url','developers/developer/id','developers/developer/name','scm/connection','scm/developerConnection','scm/url'):
            value=pom.findtext('/'.join('m:'+p for p in xpath.split('/')),namespaces=NS)
            self.assertTrue(value and value.strip(),xpath)
        for dependency in pom.findall('m:dependencies/m:dependency',NS):
            self.assertEqual('test',dependency.findtext('m:scope',namespaces=NS))

    def test_normal_build_does_not_require_signing_or_publishing_credentials(self):
        pom=ET.parse(ROOT/'java/pom.xml').getroot()
        plugins={p.findtext('m:artifactId',namespaces=NS) for p in pom.findall('m:build/m:plugins/m:plugin',NS)}
        self.assertFalse({'maven-gpg-plugin','central-publishing-maven-plugin'} & plugins)
        self.assertIsNone(pom.find('m:distributionManagement',NS),'No retired OSSRH endpoint')

if __name__=='__main__':
    unittest.main(verbosity=2)
