# Extreme representable modulus regression

The original Java fold loop allocated a comparison threshold with `BigInteger.ONE.shiftLeft(2 * bits)`. For a positive accepted modulus with `bitLength() == 1_073_741_824`, the `int` multiplication overflowed. The resulting negative shift produced a zero threshold, and even reduction of zero repeatedly entered the loop.

Cycle 14 records a real test of this case, outside the default Maven suite. `ExtremeModulusProbe` constructs `BigInteger.ONE.shiftLeft(1_073_741_823)`, confirms its bit length, and reduces zero. The subprocess runs with `-Xmx512m` and a ten-second watchdog:

| Snapshot | Observed result |
|---|---|
| `14-extreme-modulus-red` | Printed the expected bit length, then was killed by the watchdog after 10.032 seconds |
| `14-extreme-modulus-green` | Printed the same bit length and `result=0`; exit 0 after 0.343 seconds |

The red outcome is a confirmed watchdog termination after allocating the test integer, not an out-of-memory error or a compilation failure. Python's `subprocess.run` kills and waits for the child process before reporting the timeout. The heap setting is a configured maximum, not a measured peak: the integer's magnitude itself requires approximately 128 MiB, and Java intermediates and runtime overhead require additional memory. The probe is deliberately opt-in to avoid imposing this resource cost on ordinary consumer builds.

The fix uses the exact predicate:

```java
value.signum() >= 0 && (long)value.bitLength() > 2L * bits
```

For a nonnegative integer, `value >= 2^(2*bits)` is equivalent to `value.bitLength() > 2*bits`. Using `long` for the multiplication prevents overflow for every representable Java bit length, and the comparison needs no giant threshold allocation. The sign check preserves the Python loop's behavior for negative values.

After the change, cycle 15 reran all **3,868** arithmetic assertions successfully.

## Replay

From `evidence/arithmetic`, using new output directories:

```powershell
python -B run_extreme.py red --jdk 'C:/Program Files/Java/jdk-24' --source 14-extreme-modulus-red/snapshot/hashrope/PolynomialHash.java --output C:/Users/User/AppData/Local/Temp/hashrope-extreme-replay-red
python -B run_extreme.py green --jdk 'C:/Program Files/Java/jdk-24' --source 14-extreme-modulus-green/snapshot/hashrope/PolynomialHash.java --output C:/Users/User/AppData/Local/Temp/hashrope-extreme-replay-green
```

The script refuses to replace an existing output directory. It preserves both sources, SHA-256 hashes, compile output, child output, exact commands, timestamps, elapsed time and termination outcome. This regression does not claim that arbitrarily large integers are immune to the JVM's ordinary memory or BigInteger representation limits.
