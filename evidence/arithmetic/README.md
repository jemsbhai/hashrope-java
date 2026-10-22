# Arithmetic TDD evidence

Reference: downloaded PyPI Hashrope 0.2.2 wheel, SHA-256 `710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2`.

Each cycle directory contains the exact source and test snapshot used, SHA-256 hashes, UTC timestamps, compiler/test exit codes, and unedited output. No generated class files are needed for replay. `run-cycle.ps1` produced the original recordings with JDK 24.0.1. `replay.py` recompiles each immutable snapshot in a temporary directory, checks the original compile/test outcomes, and limits compilation to 60 seconds and each test process to 30 seconds.

| Cycle | Observed red | Green behavior |
|---|---|---|
| 01 / 01b | Missing PolynomialHash class | Defaults, base validation, unsigned bytes and empty hash |
| 02 | Missing folding/product methods | Mersenne modular arithmetic against independent BigInteger modulo properties, through 4096-bit values |
| 03 | Expected 0 but got 6 for non-Mersenne modulus 17 | Exact Python folding retained in byte hashing |
| 04 | Missing power methods | Cached arbitrary-precision powers, negative exponents and noninvertible-base error |
| 05 | Missing phi methods | Inverse-free geometric accumulator, zero/one/degenerate cases, 190-bit counts and negative-count compatibility |
| 06 | Missing hashConcat methods | Concatenation properties over seeded random bytes and 150-bit lengths |
| 07 | Missing hashRepeat methods | Materialized repetition comparison and independent geometric formula for 180-bit counts |
| 08 | Missing hashOverlap methods | Overlapping hashes, zero-divisor error and Python floor-divmod for signed inputs |
| 09 | Missing required rejection of nonpositive modulus | Explicit safety boundary, preserving phi zero/one early returns |
| 10 / 10b | Refactoring only | Repeatable test counter and complete Javadocs; all assertions rerun |

The first 01 red snapshot had a hand-calculated `abc` expectation typo. Before implementation, the downloaded Python oracle established `1694847`, the test was corrected, and 01b red was run. This is preserved openly in the evidence rather than discarded. The complete final executable test harness performs **3,868 assertions**.

The final Javadoc command passed with `-Xdoclint:all -Werror`. Its first attempt exposed two missing summary descriptions, then the next attempt encountered a sandbox write restriction on an already created output directory. Both outputs are preserved; the final run used a fresh output directory and passed.

## Replay

From the arithmetic evidence directory:

```powershell
python -B replay.py --evidence . --jdk 'C:/Program Files/Java/jdk-24'
```

With a JDK on PATH, omit `--jdk`. A single cycle can be selected with `--only 03-folding-compatibility-red`.

## Compatibility notes

All hash values, moduli, bases, exponents, lengths and repetition arguments use BigInteger; no 64-bit arithmetic approximation is used. The polynomial treats Java bytes as unsigned. Callers explicitly encode Unicode, and hashing uses the encoded bytes. The implementation reproduces finite observed behavior for non-Mersenne positive moduli, negative fold operands, negative phi counts, negative powers, and signed overlap lengths, although their mathematical contract may be undefined in Python's docs.

The deliberate safety boundary is rejection of nonpositive helper moduli with IllegalArgumentException; Python can loop indefinitely for these values. This rejects some finite degenerate Python results too, to make the boundary consistent. `phi(0, alpha, p)` and `phi(1, alpha, p)` retain Python's early return even for a nonpositive p. A noninvertible negative power maps Python ValueError to IllegalArgumentException. A zero overlap pattern length maps Python ZeroDivisionError to ArithmeticException with the same message.

## Additional parity review cycles

Cycles 11 and 12 cover extensible hashers, substring traversal, and precise overlap dispatch; see [EXTENSION-PARITY.md](EXTENSION-PARITY.md). Cycle 13 reruns the arithmetic regression suite. Cycle 14 reproduces and repairs integer overflow in the extreme-modulus fold guard using an opt-in watchdog process; cycle 15 reruns all 3,868 arithmetic checks. See [EXTREME-MODULUS.md](EXTREME-MODULUS.md).

There are now 23 standalone javac/java snapshots, four Maven red/green snapshots, and two isolated extreme-modulus snapshots. Use replay.py for the standalone snapshots, replay_maven.py for the Maven snapshots, and run_extreme.py for the opt-in resource regression. Each has its own recorded outcomes and source hashes.
