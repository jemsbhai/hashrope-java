# Java 0.3.4 parity-only release

Coordinates: `io.github.jemsbhai:hashrope:0.3.4`, Java 17, with no runtime dependencies. Published on Maven Central on 2026-10-24 and verified from the public repository. All coordinated release checks passed.

## Scope and compatibility

This release aligns package versions across Hashrope distributions. Java runtime algorithms, APIs, fingerprints, immutable profile checks and exact-comparison behavior are unchanged from [0.3.3](RELEASE-0.3.3.md). `Hashrope.VERSION` now reports `"0.3.4"`. Package metadata, current installation examples and release checks follow that version; historical fixtures and release evidence remain intact.

The [coordinated release notes](https://github.com/jemsbhai/hashrope/blob/master/releases/0.3.4/README.md) describe the sibling Rust implementation's accepted LCP and concatenation optimizations. Those are Rust-only changes, not Java optimizations. Rust's investigation still discloses an approximately 2% pinned independent-equal LCP signal and mixed 100 KB comparison estimates. These measurements do not establish a universal speedup, performance equivalence between languages or the absence of every small regression. Java's [0.3.3 efficiency diagnostic](../evidence/release-0.3.3/EFFICIENCY.md) remains historical evidence; no new Java speed claim is made for this version.

Exact comparisons continue to use byte and structural proofs. Shared immutable ranges and compressed repetitions can avoid scanning logical content; independent equal inputs can require a full byte scan. No universal logarithmic exact-comparison bound is claimed.

## Validation and signed bundle

The existing gates passed locally with JDK 24.0.1 targeting Java 17, Maven 3.10.0 and Python 3.12.2:

- 10,618 library tests, including unchanged numerical fixtures and exact/profile regressions.
- Three consumer tests against the installed 0.3.4 JAR.
- Four release-packaging and 22 bundle-verifier tests.
- Main, source and Javadoc JAR creation, Javadoc doclint, exact MIT notices and Java 17 class-file versions.
- All 24 signed-bundle entries, required checksums and four detached signatures, checked using an isolated public-only keyring.

[Validation provenance](../evidence/release-0.3.4/validation.json) binds source, fixture, artifact and log hashes. All 18 runtime class entries were compared with the SHA-256-verified public 0.3.3 JAR: the only changed class is `Hashrope`, whose source change is the version constant. Every algorithm class is byte-identical. The signed main, source and Javadoc JARs match the tested artifacts; the source JAR matches the working source files.

The signed bundle is `java/target/central-publishing/central-bundle.zip`, SHA-256 `9749fc47d485abe39a553cfaa44987c997ba88b87fe5cbba2374ae2dbd54713e`. [Signature verification](../evidence/release-0.3.4/bundle-verification.json) uses public signing fingerprint `3C8AD6A286A2FB052BF081B29D6C450FD82C949C`. [Commands and retained logs](../evidence/release-0.3.4/README.md) describe reproduction.

The `central-release` Maven profile defaults to `central.skipUpload=true`. Its deploy lifecycle creates a local signed bundle for review; actual upload and publication remain separate coordinated steps.

The first signing attempt failed with `No pinentry`. The installed pinentry executable and configured path were valid; reloading the existing GPG agent and signing through its normal private prompt resolved it without changing key material or configuration. Both attempt logs remain recorded. No passphrase was captured in commands, source or logs.

## Publication and public verification

The reviewed source commit is `82e771bdae1f6293f7328bc4ee488e6317abe8c0`, also the unchanged `v0.3.4` tag target. Source PR [#2](https://github.com/jemsbhai/hashrope-java/pull/2) merged at `aa0708905ee629654a4d38654146cf3a8b1d7b42`. Both Java 17 and Java 24 [CI jobs passed](https://github.com/jemsbhai/hashrope-java/actions/runs/40133391092); the coordinated release owner confirmed all package gates passed.

Central deployment `52121508-fc68-459e-ac68-1cf275959923`, named `hashrope-0.3.4-parity-release`, was observed as **PUBLISHED** at 21:58:38 UTC on 2026-10-24. The coordinator observed VALIDATING then PUBLISHING without submitting or resubmitting; the observations do not establish whether a user click or automatic mode initiated publishing. The earlier [file-picker failures and pending snapshot](../evidence/release-0.3.4/public-central/upload-preparation.json) remain unchanged as history. They concern the browser tool, not the package or signatures.

The [public verification record](../evidence/release-0.3.4/public-central/README.md) passed at 21:59:22 UTC. All 24 canonical public files are byte-identical to the frozen signed bundle, including checksum and signature files; four detached signatures verify. A new consumer project with an empty Maven cache and credentials-free Central-only settings passed all three tests with no failures, errors or skips. Its resolved POM, main, source and Javadoc artifacts exactly match the signed bundle, and all 230 repository provenance files identify only Central. The public endpoint returned HTTP 200 before the verifier ran.

This evidence update changes no Java runtime source, fixtures, signed artifacts or release tag. The public JAR is the same artifact that passed the local library and installed-JAR gates.
