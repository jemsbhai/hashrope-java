# Java 0.3.4 local release validation

This is a parity-only package release. The runtime source change relative to `194857e` is `Hashrope.VERSION` from 0.3.3 to 0.3.4. All other runtime source bytes, numerical fixtures and algorithm class bytes are unchanged. Historical release evidence remains in its original directories.

Results: 10,618 library tests, three installed-JAR consumer tests, four packaging tests and 22 bundle-validator tests passed. Maven built all three JARs and passed Javadoc doclint. `validation.json` records source/artifact/log hashes and the public 0.3.3 reference JAR used for class-byte comparison. `bundle-verification.json` records the four verified signatures, 24 entries and checksums. Publication is a separate coordinated step and has not been performed by this validation.

Commands used from the repository root, with JDK 24.0.1 and Maven 3.10.0:

```text
mvn -B -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -Dmaven.repo.local=.m2 -f java/pom.xml clean install
mvn -B -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -Dmaven.repo.local=.m2 -f consumer-smoke/pom.xml clean verify
python -B -m unittest discover -s tests -p test_release_packaging.py -v
python -B -m unittest discover -s tests -p test_verify_central_bundle.py -v
mvn -B -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -Dmaven.repo.local=.m2 -f java/pom.xml -Pcentral-release deploy
python -B tools/verify_central_bundle.py --bundle java/target/central-publishing/central-bundle.zip --public-key evidence/release/public-signing-key.asc --fingerprint 3C8AD6A286A2FB052BF081B29D6C450FD82C949C --output evidence/release-0.3.4/bundle-verification.json
```

The first offline signing attempt (`bundle-build.log`) failed because the existing GPG agent could not launch pinentry. The configured executable was present and working. Reloading the agent and using its normal private signing prompt succeeded; `bundle-build-agent-reload.log` records the completed bundle and explicit upload skip. No GPG configuration, key material or passphrase was stored or changed by the repair.

Maven's informational Javadoc warning about the obsolete `-1.1` switch is retained in the logs; Javadoc completed successfully with doclint enabled. The bundle and JAR binaries remain in ignored build directories rather than Git. Do not rebuild or replace the reviewed bundle when coordinating publication; verify its recorded SHA-256 first.
