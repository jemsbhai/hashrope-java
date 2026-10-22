# Central bundle verifier test-first evidence

The verifier was written after an executed missing-module red run. Archive/content checks reached green before adding signature security tests; those new tests failed for the missing PublicKeyVerifier class before its implementation. The last step refactored ZIP resource handling and reran all tests.

| Snapshot | Expected observed outcome |
|---|---|
| 01-contract-red | Exit 1: verifier module absent; FileNotFoundError |
| 01-contract-green | Exit 0: 12 archive/artifact contract tests pass |
| 02-signatures-red | Exit 1: PublicKeyVerifier absent; signature tests raise AttributeError |
| 02-signatures-green | Exit 0: all 22 tests pass |
| 03-resource-refactor | Exit 0: all 22 tests pass after context-managed ZIP cleanup |

Every snapshot contains the exact test and implementation files present at execution, its full console output and exit code. `manifest.json` records source hashes and expected failure markers. Run `python -B evidence/release/validator/replay.py` from any directory; it verifies source hashes, copies each snapshot into a fresh temporary directory and checks the observed exit and marker. No network, original Python checkout or signing key is needed for these unit-test replays.

The tests verify the exact four Maven artifacts and coordinates; required checksums and optional signature checksum correctness; rejection of extra, duplicate or unsafe entries; required POM metadata; absence of runtime dependencies; Java 17 class format and package boundaries; source and API documentation entries; and MIT license text in every JAR. Signature tests mock only the GPG process and check isolation, command flags, exact primary-key identity, legitimate subkeys, missing/bad/expired/revoked signature rejection and refusal to import secret-key packets.

Actual bundle verification additionally invokes real GPG, importing only the downloaded public key into a new temporary public-only keyring. It uses `--no-options`, `--homedir`, `--no-auto-key-retrieve`, `--no-autostart`, batch mode and captured status output. It never accesses the user's private keyring or prints raw GPG diagnostics. The verifier supports normal definite-length OpenPGP public-key packets; unsupported partial-length packets fail explicitly before import. Bundle and expanded archive inspection is capped at 64 MiB, well above this release's size.

Example:

```text
python -B tools/verify_central_bundle.py --bundle java/target/central-publishing/central-bundle.zip --public-key evidence/release/public-signing-key.asc --fingerprint 3C8AD6A286A2FB052BF081B29D6C450FD82C949C --output evidence/release/bundle-verification.json
```

The output must be a new file. `actual-bundle-initial.json` records the first real-bundle verification before final release integration; the final release report identifies the exact final bundle hash. A local verification result is not a Central Portal VALIDATED or PUBLISHED result.
