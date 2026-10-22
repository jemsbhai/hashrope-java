# Differential and property tests

`java/src/test/java/hashrope/DifferentialTest.java` executes the complete pinned Python fixture corpus: 10,451 results and 93 exceptions. A separate test verifies fixture counts, unique identities, and exact SHA-256 values. Every sliding snapshot replays its trace independently, so test ordering does not affect results.

`property-src/hashrope/PropertyTest.java` provides 17 additional tests across four hash profiles. They exercise 360 deterministic edit operations, 180 stream operations, 140 uneven leaf joins, and virtual repeat/split/query operations beyond 2^103 bytes for each profile. Independent byte-array and plain modular-arithmetic oracles check results and node invariants. A final case verifies defensive copies, persistence, and UTF-8 byte indexing.

The early arithmetic probe passed all 2,092 arithmetic fixture rows with zero failures. It ran against the source snapshot in `evidence/arithmetic-probe-green` before the remaining rope APIs were complete. This is regression evidence; it is not represented as a manufactured red/green cycle.

The early rope probe passed all 7,743 rope, split, and substring fixture rows with zero failures. `evidence/rope-probe-initial` records the exact source snapshot and output, including exact tree height and weight matches and shape-dependent invalid-range behavior.

Full suite status: **10,562 tests passed; zero failures, errors, or skips**. Maven reported 10,545 tests in `DifferentialTest` and 17 in `PropertyTest`, with a 5.117-second total build time. `evidence/full-initial` preserves the tested source/test snapshot, POM, exact command, output, exit code, and Surefire reports. The source snapshot combined the root staged implementation with the sliding agent's completed seven-cycle core.

`run-tests.ps1` copies staged sources into this isolated test project and preserves evidence for each uniquely named run. These differential/property cases were written before their production dependencies were complete. Their first complete execution passed; no production defects were found or changes made by this test task. The implementation agents' separate failing-test cycles provide the feature-level red/green evidence.

Exception mapping: Python `ValueError` maps to `IllegalArgumentException` with library diagnostic text preserved. `ZeroDivisionError` maps to `ArithmeticException`; null-object `AttributeError`/`TypeError` maps to `NullPointerException`. Runtime-generated null/division messages are not required to match across languages. Invariant failures remain `AssertionError` with exact messages.
