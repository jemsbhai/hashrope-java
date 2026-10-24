# Java 0.3.3 release validation

This release candidate is built, tested, signed and verified locally; publication is a separate step. Coordinates are `io.github.jemsbhai:hashrope:0.3.3`, compiled for Java 17 with no runtime dependencies.

## Changes and compatibility

- `Rope.equal` / `Rope.lcp` compare actual bytes across different tree shapes and hash profiles. `Rope.rangeEqual` / `Rope.rangeLcp` accept BigInteger or long coordinates and reject invalid ranges before identity shortcuts.
- The comparison engine uses an explicit heap stack, reference-identity cache keys, proven-equal range memoization, native leaf range comparison and finite Fine-Wilf witnesses for repeated spans. Independent self-concatenation DAGs remain compressed; the comparison itself does not call recursive structural equality/hashCode, split, materialization or hash functions.
- `HashProfile`, `PolynomialHash.profile()` and `Node.profile()` record immutable constructor parameters. Mixed-profile construction, composition, split, substring hashing and optional-context validation now fail explicitly. Streaming checks its original profile and retained root, including empty appends/copies. Replacing a hasher with equal parameters remains valid.
- Defaults, byte encoding, existing fingerprints, node sharing and structural `equals`/`hashCode` are unchanged. The pinned Python 0.2.2 fixture files were not regenerated or edited. Non-Mersenne numerical quirks retain the Java/Python baseline behavior; exact comparisons never use these fingerprints as decisions.
- Existing hasher and stream subclass dispatch remains supported. Profiles identify constructor parameters, not arbitrary subclass algorithms; a subclass remains responsible for its own arithmetic invariants. Direct mutation of unrelated public stream fields retains its previous risks.

The rejection of mixed profiles is an intentional safety change: earlier releases silently combined incompatible fingerprints, including full-node substring shortcuts. A stream that needs different parameters must be constructed with them; assigning a new profile over an existing prefix is rejected.

## Efficiency scope

Shared immutable ranges and periodic spans can be skipped after exact structural/byte proofs. Independent equal inputs may require reading their entire content, plus traversal, memoization and BigInteger costs. Auxiliary memory follows the proof stack and visited equal ranges. No universal logarithmic exact-comparison bound or cross-language speed equivalence is claimed. The new comparator is stack-safe; historical edits, structural validation and materialization retain their recursion/array-size limits. A profile adds one shared immutable reference per node; this release does not claim unchanged object layout or total memory use.

## Validation

At the locally tested candidate:

- 10,618 library JUnit tests passed, including all 10,544 pinned golden/error records, the existing subclass-dispatch tests and 10 new exact/profile tests.
- Three independent consumer tests passed using the installed 0.3.3 JAR, including collision rejection and access to the new exact/profile API.
- Four release-packaging and 22 Central-bundle-validator unit tests passed. The validator now requires 0.3.3 coordinates and the new public HashProfile class in binary, source and Javadoc artifacts.
- Maven `clean install` built the main, sources and Javadoc JARs; Javadoc doclint passed. The package retains exact MIT notices and Java 17 class-file versions.

The exact tests cover default-profile collisions; every mismatch position in representative leaf sizes; independent chunked shapes with random ranges and different profiles; 160-bit repeats with differing periods and shifted boundaries; 180-level self-concatenation DAGs and late edits; 5,000-deep manually constructed trees; bounds/empty/identity cases; non-Mersenne shape-dependent fingerprints; and profile-replacement rejection without changing stream state.

A six-pair retained-API [efficiency diagnostic](../evidence/release-0.3.3/EFFICIENCY.md) records every sample and its frozen sources. Split/rejoin's median is 1.2% higher (five of six pairs slower); other medians are lower with substantial spread. These limited observations are disclosed without a general speedup or no-overhead claim.

Commands from the repository root:

```text
mvn -B -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -f java/pom.xml clean install
mvn -B -ntp -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -f consumer-smoke/pom.xml clean verify
python -m unittest discover -s tests -p test_release_packaging.py -v
python -m unittest discover -s tests -p test_verify_central_bundle.py -v
```

Build and consumer logs are in `evidence/release-0.3.3/`. The first intermediate run captured two obsolete tests that expected mixed profiles to be accepted; those tests now assert the documented rejection while retaining their other checks. No source correctness failure was observed in the final suite. Historical 0.2.2 publication, signatures and fixture provenance remain in `RELEASE.md`, `VERIFICATION.md` and the fixture manifest.

## Offline signed bundle

The opt-in Central profile created `java/target/central-publishing/central-bundle.zip` with upload skipped. Its SHA-256 is `2566cbcbb7aec38a9f7f9d5c351187dba02efcfd9345c03bb985bbce5282fb01`. The hardened verifier checked all 24 entries, all required checksums and four signatures against public fingerprint `3C8AD6A286A2FB052BF081B29D6C450FD82C949C`. Main, source and Javadoc JAR bytes match the tested artifacts exactly; the source JAR matches the working sources. See [bundle verification](../evidence/release-0.3.3/bundle-verification.json) and [validation provenance](../evidence/release-0.3.3/validation.json). This is local verification, not confirmation of Central publication.

The first offline build attempts recorded a PowerShell argument-quoting error and an unavailable pinentry executable. A routine GPG agent path correction allowed the existing key to be unlocked privately; the final build succeeded without changing signing credentials or publishing anything. All attempt logs are retained.
