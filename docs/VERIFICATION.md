# Initial implementation verification

This report records the initial 2026-10-22 implementation handoff before release preparation and publication. Its local artifact hashes and delivery statements are historical. The subsequent signed release and public Central consumer verification are recorded in [RELEASE.md](RELEASE.md#verified-public-release).

Completed locally on 2026-10-22 in `E:/data/code/claudecode/hashrope-java`, using JDK 24.0.1, Maven 3.10.0 and Java release 17. The Java package is `hashrope`; coordinates are `io.github.jemsbhai:hashrope:0.2.2`. The authenticated GitHub account was verified as `jemsbhai`, consistent with the supplied namespace. No Maven Central publication or namespace claim was attempted.

## Results

| Check | Passed | Failed / errors / skipped | Evidence |
|---|---:|---|---|
| Library JUnit suite | 10,608 | 0 / 0 / 0 | [Maven log](../evidence/final-maven.log), [suite totals](../evidence/artifact-verification.json) |
| Independent consumer JUnit suite | 2 | 0 / 0 / 0 | Same log; separate Maven project resolves only the installed artifact |
| Arithmetic assertions inside library suite | 3,868 | 0 | [Arithmetic evidence](../evidence/arithmetic/README.md); not additional JUnit cases |
| Published Python sdist tests against pinned wheel | 146 | 0 / 0 / 0 | [Reference report](../evidence/reference/README.md) |
| Fixture generator unittest suite | 3 | 0 / 0 / 0 | [Final log](../evidence/fixture-generator-final.log) |
| README Java examples | 5 | 0 | [Compiled source](../evidence/docs/DocExamples.java), [execution log](../evidence/docs/examples-run-final.log) |
| Strict javac `--release 17 -Xlint:all -Werror` | Passed | No diagnostics | [Log](../evidence/javac-lint.log) |
| Strict javadoc `-Xdoclint:all -Werror` | Passed | No diagnostics | [Log](../evidence/javadoc-strict.log) |
| Fresh fixture regeneration | Passed | Byte-identical manifest and TSVs | [Artifact report](../evidence/artifact-verification.json) |
| Source preservation | Passed | No mismatches | [Aggregate report](../evidence/original-preservation-final.json) |

The 10,544 differential records comprise 10,451 expected values and 93 expected errors. The additional differential test validates manifest hashes and complete corpus consumption. Deterministic property tests check edits, balancing, persistence, large compressed repetitions, substring traversal and streaming against independent models. Review regressions verify callback order, primitive dispatch, array ownership and extreme arithmetic; [all four review findings are resolved](../evidence/FINAL-REVIEW.md).

The Maven Javadoc plugin emitted one tool-compatibility warning about its obsolete `-1.1` switch. The separate strict Javadoc invocation succeeded without diagnostics. This is not a source documentation warning.

## Commands and reproducibility

From the repository root, PowerShell:

```powershell
./tools/verify.ps1 -Maven 'E:/tools/apache-maven-3.10.0/bin/mvn.cmd' -JavaHome 'C:/Program Files/Java/jdk-24' -Repository 'C:/Users/User/Documents/Codex/2026-10-22/task/m2'
```

The script supplies empty user and global Maven settings, builds and locally installs all three library JARs, then builds/tests the separate consumer. It does not run deploy, sign artifacts or read saved publishing configuration. Portable Maven commands appear in the README.

[TDD.md](TDD.md) explains every cycle and portable replay command. Final replay logs cover [16 core snapshots](../evidence/core-tdd-replay-final.json), [23 standalone arithmetic snapshots](../evidence/arithmetic-full-replay-final.log), [25 sliding snapshots](../evidence/sliding-full-replay-final.log) and [both fixture-generator red/green pairs](../evidence/fixtures-full-replay-final.log). Expected historical red failures are retained; they are not outstanding test failures. Additional arithmetic Maven and opt-in watchdog outcomes are retained in their module evidence.

The authoritative [PyPI lookup](../evidence/reference/registry-verification.json) resolved latest stable **0.2.2**, and both distribution hashes matched the pinned manifest. Generation and reference testing imported only the downloaded wheel outside the original checkout. Python bytecode and pytest caches were disabled. The complete fixture corpus was regenerated again from that wheel and compared byte-for-byte with the delivered files.

## Built artifacts

| Local artifact | Bytes | SHA256 |
|---|---:|---|
| `java/target/hashrope-0.2.2.jar` | 19,285 | `26125c0c92c1cc0bbb63c43e588c491dcfb4c33d7e7beb8cbb1245a4e9f946c9` |
| `java/target/hashrope-0.2.2-sources.jar` | 15,208 | `bbf0bc0462d313de7b4904cfc5090b4f3c401d260cb40d5fa86e17a6878b0d88` |
| `java/target/hashrope-0.2.2-javadoc.jar` | 148,797 | `17a2a0ad73279f26d063bde0522517fd42884ecb002e8a1f7ad7c6fc2f73d7bf` |

All ZIP entries pass integrity checks. Main classes are exclusively in `hashrope` and use class-file major version 61 (Java 17); no test classes are packaged. The source archive contains the exact delivered Java source bytes. Javadoc contains the API pages and index. Generated JARs are local build outputs, excluded from Git; consumers can reproduce them with Maven.

## Limits and delivery

No supported behavior remains knowingly unimplemented within the [compatibility contract](COMPATIBILITY.md). Explicit boundaries remain: Java byte-array/BigInteger/heap/stack limits, static typing, sealed immutable node types, Java exception/object representation mappings, and rejection of nonpositive folding moduli where the Python reference can fail to terminate. The complete [parity matrix](PARITY-MATRIX.md) identifies these decisions. Nonterminating Python cases and deliberate heap/stack exhaustion are not run. The isolated extreme-modulus watchdog regression was run successfully outside the ordinary suite.

Local tests used JDK 24.0.1 targeting Java 17. GitHub Actions is configured to test both Java 17 and 24; hosted execution status is reported at final delivery rather than inferred here.

The original repository and its dirty nested repository retain all 748 inventoried files, six Git metadata files, HEADs, branches and statuses. No original files, configuration, index, remote or caches were changed. The detailed file inventory stays local and is excluded from the public repository. An initially denied general directory-copy approach was replaced with a preflighted exclusive-create transfer: 883 new files, zero overwritten files, no unresolved transfer block.

The new GitHub repository is [jemsbhai/hashrope-java](https://github.com/jemsbhai/hashrope-java), **public** at the user's explicit request. Push verification records the final local and remote commit IDs separately. No release tag, deployment, Maven Central publication or credential creation/configuration was performed.

Creating/associating a Codex sidebar project named `hashrope` was unavailable: the project registry had no matching project, the provided tools expose no project-creation action, and native application control is disabled. Implementation and GitHub delivery continued; no undocumented internal database was modified.
