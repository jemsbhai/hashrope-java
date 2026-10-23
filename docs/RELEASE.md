# Maven Central release procedure

Coordinates: `io.github.jemsbhai:hashrope:0.2.2`. This procedure applies only to the separate Java repository. The original Python checkout remains read-only.

## Verified public release

Version **0.2.2** is [available on Maven Central](https://central.sonatype.com/artifact/io.github.jemsbhai/hashrope/0.2.2), independently verified on **2026-10-23**. Release source: [`e111f3e773db114b6932692f52dfb7f433e7d7be`](https://github.com/jemsbhai/hashrope-java/commit/e111f3e773db114b6932692f52dfb7f433e7d7be). Later documentation commits do not change these immutable artifacts. Do not upload this version again.

- All **24 public files** returned HTTP 200 and are byte-for-byte identical to the prepared bundle: POM, main/sources/Javadoc JARs, four signatures and 16 checksums. All nine Java source files match the release commit. See [artifact comparison](../evidence/release/public-artifact-comparison.json).
- All **four signatures** verify with the freshly retrieved public key, fingerprint `3C8AD6A286A2FB052BF081B29D6C450FD82C949C`. See [signature and artifact validation](../evidence/release/public-bundle-verification.json). The downloaded files were zipped for verification; that ZIP has different container metadata, but every published file matches the original signed bundle.
- The separate consumer passed **2 tests, zero failures/errors/skips**, using a newly created empty cache, JDK 24.0.1 and Maven 3.10.0. A credentials-free mirror routed every Maven repository to public Central. Every cached artifact has Central provenance; the consumed library POM/JAR match the bundle. No library installation or staging repository was used. See [consumer report](../evidence/release/central-consumer-verification.json) and [build log](../evidence/release/central-only-consumer.log).
- Prior release validation passed **10,608 library tests** and **29 Python tooling tests**; the release source passed [Java 17 and 24 CI](https://github.com/jemsbhai/hashrope-java/actions/runs/39860890842). This follow-up changed only documentation and evidence.
- The original checkout and its nested repository remain unchanged: **748 files, six Git metadata files, HEADs, branches and statuses**. See [preservation summary](../evidence/release/original-preservation-published.json).

The user published the existing deployment `a9e34487-d27f-4fc8-9f43-62d61156c668` (`hashrope-0.2.2-e111f3e`). Its last directly observed Portal badge was `PUBLISHING` on 2026-10-22. Authenticated browser tools were unavailable after reconnection, so the final Portal badge was not re-read. Public release availability, exact artifact contents and consumer resolution were verified directly. No duplicate deployment or credential configuration was created. The [publication record](../evidence/release/publication-verification.json) preserves this distinction.

To repeat the public consumer check, substitute a directory that does not yet exist:

```text
mvn -B -ntp -s evidence/release/central-only-settings.xml -gs tools/isolated-settings.xml -Dmaven.repo.local=<NEW_EMPTY_DIRECTORY> -f consumer-smoke/pom.xml clean verify
```

The Codex `hashrope` sidebar project now exists. Task association was attempted through the supported tool, which reported that the task's source project could not be safely determined; association remains unconfirmed. No internal application database was modified.

## Build and inspect

The opt-in `central-release` profile uses Central Portal publisher **0.12.0**, which explicitly fixes Maven 3.10.0 bookkeeping files in deployment bundles. GPG plugin **3.2.8** signs the POM and all three JARs with the existing key; `bestPractices=true` rejects unsafe passphrase configuration. [Sonatype compatibility notes](https://central.sonatype.org/publish/publish-portal-maven/#0120-13-oct-2026), [Apache GPG documentation](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html).

From the repository root, with Maven and Java configured:

```text
mvn -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -f java/pom.xml -Pcentral-release clean deploy
python -B tests/test_release_packaging.py
python -B tools/verify_central_bundle.py --bundle java/target/central-publishing/central-bundle.zip --public-key evidence/release/public-signing-key.asc --fingerprint 3C8AD6A286A2FB052BF081B29D6C450FD82C949C --output bundle-verification.json
```

The profile defaults `central.skipUpload=true`: even the deploy lifecycle creates only a local bundle. It requires no Portal token. The existing GPG agent handles private key use; any necessary passphrase is entered only in its private local prompt. Batch mode requires an already unlocked agent. Never put secrets in Maven properties, process arguments, logs, source files or chat; never enable debug logging for authenticated publishing.

The bundle contains only the repository-layout directory `io/github/jemsbhai/hashrope/0.2.2/`, with the POM, main/sources/Javadoc JARs, four detached signatures, and MD5/SHA1/SHA256/SHA512 checksums. All JARs contain the project MIT notice. The verifier checks exact contents, metadata, classes and documentation, checksum integrity, and signatures using only a downloaded public key in an isolated temporary GPG home.

## Publish

Check the public Maven repository for the exact version immediately before uploading; Central releases are immutable. Upload the inspected `java/target/central-publishing/central-bundle.zip` through the existing authenticated [Central Portal Deployments page](https://central.sonatype.com/publishing/deployments). Browser bundle upload is [officially supported](https://central.sonatype.org/publish/publish-portal-upload/).

`VALIDATED` means the bundle passed checks and still awaits publication. `PUBLISHING` is in progress. Only `PUBLISHED` confirms the Portal publication step; then verify the public repository independently. If new legal terms, credentials or persistent-access configuration become necessary, obtain the required user confirmation. Do not overwrite existing Maven settings or generate another token merely to bypass a blocker.

## Verify public consumption

Build `consumer-smoke/pom.xml` using the supplied Central-only user settings, empty global settings and a new, empty local Maven repository. Do not install this library there or add staging repositories. Run `clean verify`, inspect `_remote.repositories` for Central provenance, and compare the downloaded POM/main JAR to the inspected signed bundle. Also retrieve and verify the public sources/Javadoc/signatures/checksums. Record the exact source commit, deployment state, public URL and consumer results in `evidence/release/`.

The initial implementation evidence in `docs/VERIFICATION.md` remains historical. Current release results are recorded separately; upload or local installation alone is never reported as publication success.
