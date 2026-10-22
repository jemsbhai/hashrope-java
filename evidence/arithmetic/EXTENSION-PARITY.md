# Hasher subclass dispatch and substring traversal

## Test-first changes

Cycle 11 first added `SubstringTraversalTest` and recorded the expected compiler failure: `cannot inherit from final hashrope.PolynomialHash`. Removing only the class-level `final` restriction made all four tests pass. The parameter fields remain private and final, and the concurrent power cache is unchanged.

The tests construct their ropes with a normal hasher, then supply a subclass to substring hashing. The subclass counts calls to `hash(byte[])`, input bytes, and `prime()` reads. The latter occurs once at each entry to `Rope.hashRange`, providing a traversal counter for the tested cases:

| Query | Bytes rehashed | Hash calls | Range visits |
|---|---:|---:|---:|
| Entire 1,024-leaf root | 0 | 0 | 1 |
| Spanning range with two partial boundary leaves | 98 | 2 | At most `4 * height + 2` |
| Prefix ending 17 bytes before the end | 47 | 1 | At most `2 * height + 1` |
| Repeat range with length `7 * 2^120 + 4` | 11 | 2 | 3 |

The huge-repeat test is bounded by a ten-second JUnit timeout and compares against an independently composed hash of a rotated period. It cannot materialize the logical range; the byte counter confirms that only eleven boundary bytes reach the byte-hashing method.

Cycle 12 then addressed a subclass dispatch mismatch identified by independent review. Python `hash_overlap` calls `power(d)`, `power(r)`, and the static geometric/reduction primitives directly. It does not dispatch through `hash_repeat` or `hash_concat`. The initial Java implementation had reused those composite methods. The new `PolynomialHashDispatchTest` overrides both to throw: its first run recorded two failures in three tests. Replacing the composite calls with the Python primitive call sequence made all three tests pass, including an override of `power` whose return values and invocation order are observable.

## Scope of the allocation claim

A source audit of `Rope.substrHash`/`Rope.hashRange` confirms that the query path contains no `Leaf`, `Internal`, or `RepeatNode` construction and invokes no `concat`, `split`, or `repeat` rope construction routine. Complete nodes return stored hashes; partial leaves provide byte slices; internal nodes combine recursive results; repeat nodes combine boundary hashes and geometric arithmetic. The called polynomial-hash routines construct no rope nodes.

This is a source-based claim about **no new rope nodes**, not a measurement of all heap allocations. Byte slices, BigInteger arithmetic, cached powers, test instrumentation, and other ordinary Java objects may allocate. The traversal counters validate the tested fast paths and boundary behavior; they are not an allocation profiler or a universal complexity proof.

## Replay

From `evidence/arithmetic`:

```powershell
python -B replay_maven.py --maven 'E:/tools/apache-maven-3.10.0/bin/mvn.cmd' --jdk 'C:/Program Files/Java/jdk-24' --repository 'C:/Users/User/Documents/Codex/2026-10-22/task/m2' --offline
```

For a fresh machine, omit `--offline` and use a writable local Maven repository. Replay verifies each recorded source hash, copies the project into a temporary directory, uses isolated Maven settings, enforces a 90-second process timeout, and checks the precise red failure marker or successful JUnit report. No evidence snapshot is rebuilt in place.
