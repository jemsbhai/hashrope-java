# Java 0.3.4 public Maven Central verification

**Published and publicly verified on 2026-10-24.** [Maven Central](https://repo.maven.apache.org/maven2/io/github/jemsbhai/hashrope/0.3.4/) serves `io.github.jemsbhai:hashrope:0.3.4`. Deployment `52121508-fc68-459e-ac68-1cf275959923` was observed PUBLISHED at 21:58:38 UTC; the canonical POM first returned HTTP 200 at 21:58:46 UTC, and full verification finished at 21:59:22 UTC.

All **24 public files exactly match the frozen signed bundle**, including checksum and signature files. All **four signatures** verify with fingerprint `3C8AD6A286A2FB052BF081B29D6C450FD82C949C`. A new consumer project and empty Maven cache passed **three tests, zero failures/errors/skips**, covering exact collision rejection, ranges/profiles, huge repetitions, streaming and the installed example. Its POM/main/source/Javadoc bytes match the signed bundle. All **230 Maven repository provenance files** identify only the configured Central-only mirror; no local install, staging repository or credentials were used.

The reviewed source and `v0.3.4` tag target are `82e771bdae1f6293f7328bc4ee488e6317abe8c0`; the source PR merge is `aa0708905ee629654a4d38654146cf3a8b1d7b42`. The 210,486-byte signed upload bundle remains SHA-256 `9749fc47d485abe39a553cfaa44987c997ba88b87fe5cbba2374ae2dbd54713e`. The reconstructed verification ZIP has different ZIP metadata and its own hash; every entry's bytes match the upload bundle.

[publication.json](publication.json) binds the evidence hashes and source identities. [verification.json](verification.json), [public-artifact-comparison.json](public-artifact-comparison.json), [public-bundle-verification.json](public-bundle-verification.json), the three Maven logs, [consumer-results.json](consumer-results.json) and [remote-provenance.json](remote-provenance.json) retain the checks. The unchanged [upload-preparation.json](upload-preparation.json) is a historical pre-upload snapshot: its false upload/publication fields describe that earlier state, and its `source_commit` field names the merge commit. The coordinator later observed VALIDATING, PUBLISHING and PUBLISHED without submitting or resubmitting; a user click versus automatic publishing mode is not established by those observations. The prior chooser failures remain recorded as browser-tool limitations.

## Reproduction

The reviewed driver assumes execution from `target/public-central-0.3.4/`, two parent levels below the checkout root. Do not execute the retained evidence copy directly. From the repository root, copy it to the required layout and keep the exact reviewed upload bundle at `target/release-0.3.4-frozen/central-bundle.zip`:

```powershell
New-Item -ItemType Directory -Force target/public-central-0.3.4
Copy-Item evidence/release-0.3.4/public-central/verify_public.py target/public-central-0.3.4/verify_public.py
python -B target/public-central-0.3.4/verify_public.py --output target/public-central-0.3.4/run-new
```

Use Maven on PATH and a supported JDK. Choose a new output directory; existing outputs are rejected. The driver checks the exact frozen bundle hash, canonical HTTP responses, all 24 file bytes, four signatures, three consumer tests and Maven provenance. The accepted repository IDs are the configured Central-only ID or that ID plus the SHA-1 of the configured Central URL, as used by Maven 3.10. `retain_evidence.py` records the completed original run without altering its artifacts or cache; its fixed paths describe that run, not a generic repeat command.

Source, runtime, fixtures, signed artifacts and the `v0.3.4` tag remain unchanged by this evidence update. Raw logs are preserved verbatim, including Maven's whitespace.
