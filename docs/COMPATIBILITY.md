# Compatibility with Python Hashrope 0.2.2

This port targets the published Python distribution **Hashrope 0.2.2**, with Java package `hashrope` and Maven coordinates `io.github.jemsbhai:hashrope:0.2.2`. It translates Python names to Java conventions and preserves the release's numerical, persistent-rope and streaming behavior, including the observed malformed-range and negative-copy behavior described below. The [parity matrix](PARITY-MATRIX.md) maps each public API to its Java counterpart and verification.

**Final verification:** all 10,608 library JUnit tests and both independent consumer tests passed. Exact counts, artifact inspection, oracle regeneration and TDD replay evidence are in [VERIFICATION.md](VERIFICATION.md).

## Pinned reference

The latest stable release reported by the [authoritative PyPI registry](https://pypi.org/pypi/hashrope/json) at the 2026-10-22 lookup was 0.2.2. The fixture generator imports the downloaded wheel directly after verifying its SHA256; it does not import the original checkout.

| Artifact | SHA256 |
|---|---|
| `hashrope-0.2.2-py3-none-any.whl` | `710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2` |
| `hashrope-0.2.2.tar.gz` | `881b367afc38b5d32609d3d604e4a19ce27e4eaeb3078e54948bdad69e54b499` |

Source: [jemsbhai/hashrope](https://github.com/jemsbhai/hashrope). Original local HEAD was `100749d1cef9603465f4600b814c76d2ad6e9a6e`. The wheel's `__init__.py`, `polynomial_hash.py` and `sliding.py` are byte-identical to `packages/python/hashrope` at that checkout. The local `rope.py` differs only by three additional docstrings explaining byte indexing; the executable statements match. Exact source hashes and fixture hashes are in [the fixture manifest](../java/src/test/resources/fixtures/manifest.json).

## Bytes, integer sizes and ownership

Hashrope operates on **bytes**. A Java `byte` is interpreted as unsigned: `0x00` contributes coefficient 1 and `0xff` contributes 256. Hashing uses `H(data) = sum((byte[i]+1) * base^(n-1-i))`, reduced with the reference folding algorithm. All rope offsets and lengths count bytes. No Unicode normalization, character indexing, decoding or implicit string encoding is performed. Encode strings explicitly, for example with `StandardCharsets.UTF_8`. A valid byte split can divide a multibyte character, leaving halves that are not separately valid UTF-8.

Hashes, moduli, bases, powers, logical lengths, weights, repetition counts, split/range positions and streaming counters use `BigInteger`. Compressed ropes can therefore represent lengths beyond signed `long`; intermediate multiplication does not wrap. Convenience `long` overloads convert to BigInteger and use the same implementation. BigInteger itself remains subject to the JVM's supported magnitude and allocation limits; “arbitrary precision” does not promise an infinite amount of addressable memory or compatibility beyond the JDK's supported BigInteger range.

The [JDK 24 BigInteger specification](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/math/BigInteger.html) requires support for values strictly between `-2^Integer.MAX_VALUE` and `+2^Integer.MAX_VALUE`; the reference implementation supports that range. Operations producing unsupported values can raise `ArithmeticException`, and available memory may impose a smaller practical limit. Python integers have different implementation limits. This is an explicit Java representation boundary, not a promise of parity beyond supported magnitudes.

`Rope.toBytes` materializes a fresh `byte[]`. Its exact logical length is converted with `intValueExact()`: a size outside the signed-int range raises `ArithmeticException`. Even smaller arrays can exceed a JVM's array or heap limit and raise `OutOfMemoryError`. A leaf must already fit a Java byte array. Hashing and splitting an enormous compressed rope do not require materializing it. Recursive traversal is also subject to the JVM stack; sufficiently deep manually nested nodes can raise `StackOverflowError`. Python has its own different memory, bytes-length and recursion limits. These runtime limits are not numerical truncation.

`null` is the empty rope. Leaves defensively copy incoming bytes and `Leaf.data()` returns a defensive copy. Internal and repeat nodes share immutable children. Concatenation with an empty side, repetition by one and endpoint splits preserve the existing root reference; splitting exactly at an internal boundary returns its child references. Earlier roots remain valid after later operations.

Node `equals` is structural, matching the dataclass comparison: node type, bytes, child structure, repetition and stored hash matter. Two differently shaped trees containing the same bytes need not be equal. Java `hashCode` obeys Java equality rules; it is not the polynomial content hash and does not reproduce Python's process-specific object hash value. Use `Rope.hash` for the polynomial hash. Java `toString` is a concise Java representation, not Python's dataclass repr.

## Hash parameters and arithmetic

The default is `MERSENNE_61 = 2^61-1`, base 131. `MERSENNE_127 = 2^127-1` is also exported by `PolynomialHash`. There is no separate profile abstraction: choose an explicit prime/base pair. Construction checks only `2 <= base < prime`, as Python does. It does not prove primality or check the Mersenne shape.

For a positive Mersenne modulus and nonnegative operands, folding computes the mathematical remainder. The implementation also preserves the release's folding behavior for positive non-Mersenne moduli and negative inputs. In those domains, the result need not equal `BigInteger.mod`, and the polynomial-hash algebraic guarantees need not hold. Negative `phi` counts retain Python's bit-scanning behavior; they do not represent a mathematical negative-length geometric sum.

`power` supports negative exponents through modular inversion. An unavailable inverse raises `IllegalArgumentException` with the Python message `base is not invertible for the given modulus`. `hashOverlap` implements Python floor division/remainder even for signed dimensions. A zero pattern length raises `ArithmeticException`, including when the requested length is zero. Callers supply the partial-prefix hash themselves; it is not independently verified.

**Deliberate arithmetic deviation:** `mersenneMod` and `mersenneMul` reject nonpositive moduli with `IllegalArgumentException`. The corresponding Python folding loop can fail to terminate. `phi(0, alpha, p)` still returns zero and `phi(1, alpha, p)` still returns one before any modulus check; other counts reject nonpositive moduli. The fixture generator does not run nonterminating Python cases.

Use the same hasher parameters for every node and operation in a rope. Nodes do not record their originating context, and mixed contexts are not rejected, matching Python. `validate` does not detect a context mismatch. A PolynomialHash caches powers for its lifetime; the cache grows with distinct requested exponents. The base Java implementation's cache supports concurrent access, while a SlidingWindow instance remains mutable and not thread-safe.

PolynomialHash and SlidingWindow are extensible Java classes. Overridable hasher methods can be used to instrument hashing/traversal or customize behavior; `SlidingWindow.finalHash()` dispatches to an overridden `currentHash()`, as Python's `self.current_hash()` does. Subclasses are responsible for maintaining their own invariants and concurrency guarantees. The immutable node classes remain final and their union sealed; arbitrary Python-style node subclassing and runtime monkeypatching are not reproduced.

Polynomial hashes are not cryptographic digests. A larger modulus reduces accidental-collision probability under suitable assumptions; it does not provide adversarial collision resistance, authentication, integrity against attackers or a substitute for SHA-256/MACs. Equality of hashes alone does not prove equality of data.

## Rope behavior

`Rope.fromBytes` creates one leaf for nonempty input and returns null for empty input. Leaf weight is one regardless of byte length. Internal weight is the sum of child weights; repeat weight is child weight multiplied by repetitions. Weight therefore counts expanded leaves, not bytes or materialized node objects.

`Rope.concat` uses the pinned BB[2/7] join and checked rotations. Every internal node with total weight greater than two satisfies `2*total <= 7*leftWeight <= 5*total`. Repeat nodes decompose by repetition count when joining. The public `Internal` constructor combines metadata directly and does not rebalance; it can intentionally create a tree that `Rope.validate` rejects.

`Rope.split(node, pos, h)` clamps positions: values at or below zero give `(null,node)`, and positions at or above the logical length give `(node,null)`. Interior repeat splits handle both repetition boundaries and positions within a period. `Rope.Split.left()` and `.right()` replace Python's tuple result.

`Rope.repeat(node, 0, h)` returns null; count one returns the original node. Other counts use RepeatNode, whose count must be at least two. Nested repeats remain nested. Repeating null zero or one times returns null; repeating null two or more times fails with `NullPointerException`, matching Python's null-child failure through a Java exception mapping.

`Rope.height` returns zero for null and leaves, one plus child height for repeats, and one plus maximum child height for internal nodes. Height describes the actual tree, not the logarithm of expanded length.

### Substring ranges, including malformed input

For `0 <= start`, `0 <= length` and `start+length <= Rope.len(node)`, `Rope.substrHash` returns the hash of exactly that byte interval without constructing new rope nodes. Full-node coverage returns stored metadata; a partial repeat range combines its tail, complete periods and head. Empty input or zero length returns zero before range processing.

Python 0.2.2 does not validate malformed ranges, so this Java port does not impose new bounds checks. Leaf handling reproduces Python slicing; repeat handling may hash the periodic pattern beyond the actual repeated length; internal nodes can combine available bytes using the requested interval lengths. Results outside the valid domain are representation-dependent and should not be used as a general clipping or slicing API. They are retained for behavioral compatibility and covered by differential fixtures.

For example, a leaf containing `ababab` queried at start 6, length 1 hashes the empty slice (zero), while `RepeatNode(Leaf("ab"),3)` queried at the same coordinates hashes `a` (98 with the defaults). A negative length can produce a nonzero result. Use valid ranges for ordinary application logic.

### Validator semantics

`Rope.validate` checks the Python structural invariants: leaf length/weight; internal length, weight and BB[2/7] balance; repeat length, weight and repetition minimum; and child invariants. It throws `AssertionError` explicitly, independent of the JVM `-ea` flag. The optional hasher argument is accepted and ignored, as in the reference. The validator does not recompute stored hashes or verify matching parameter contexts. The test suite performs independent hash checks in addition to calling this compatibility validator.

## SlidingWindow behavior

Defaults are `dMax=32768`, `mMax=258`; `W=dMax+mMax`. Positive capacities are required, with the dMax check occurring first. `appendBytes` appends one copied literal leaf; null and empty arrays are no-ops, corresponding to Python's falsy-input shortcut.

`appendCopy(offset,length)` supports both a source interval already present in the window and an overlapping back-reference that repeats its source period. It checks offset against total decoded `pos` before checking retained `windowLen`. Those checks happen even for a zero-length copy. An offset of zero with a positive length raises `ArithmeticException` without changing the stream. `(0,0)` is a no-op, including on an empty stream.

Eviction occurs only when the current window length is **greater than** W. At exactly W bytes, nothing is evicted. On eviction, the prefix hash absorbs the entire removed prefix and exactly dMax bytes remain. dMax and mMax do not impose per-operation limits: an offset greater than dMax is allowed while its source remains in the current window, and a copy longer than mMax is allowed before subsequent eviction. A single literal append can likewise exceed W.

The assignable Python slots are preserved as public mutable Java fields: `h`, `dMax`, `mMax`, `W`, `hPrefix`, `lPrefix`, `rWindow`, `pos`. Accessor methods expose the same current values, with `capacity()` for W and `window()` for rWindow. Direct assignment does not enforce consistency or automatically recompute other fields. The returned window root is an immutable snapshot.

Negative copy offsets and lengths retain the observed reference behavior. They can make `pos` disagree with represented data; for example, after `abc`, copying offset 1 with length -1 reduces pos to two while retaining three bytes. These inputs are supported as compatibility quirks, not valid decoded-stream operations. Direct field mutations can likewise invalidate the stream invariant. Under valid operations, `pos-lPrefix == windowLen`, retained length is at most W, and `currentHash` equals the hash of all decoded bytes.

`finalHash()` simply returns `currentHash()`. It does not close, clear or freeze the accumulator; appending afterward is permitted.

## Error and language mappings

| Python behavior | Java behavior |
|---|---|
| Explicit library `ValueError` (base, capacities, repetitions, empty leaf, copy bounds) | `IllegalArgumentException`, preserving the library message |
| Noninvertible negative modular exponent | `IllegalArgumentException`, preserving the Python message |
| `ZeroDivisionError` | `ArithmeticException` |
| Null child/access error (`AttributeError`/null-related `TypeError`) | `NullPointerException`; JVM-specific text is not a compatibility guarantee |
| Validation `AssertionError` | Explicit `AssertionError`, enabled regardless of JVM assertion flags |
| Python int | BigInteger; long convenience overloads are additive |
| Python bytes | byte[] with defensive leaf ownership; bytes remain unsigned semantically |
| Frozen dataclass attributes | Read-only Java accessors on final classes |
| Python tuple from split | `Rope.Split` record |
| Python `Node` Optional union | Sealed Node interface plus null |
| Python dynamic input types/custom monkeypatching | Statically typed signatures and sealed/final node types; runtime monkeypatching not reproduced |

The byte[] API excludes Python's accidental acceptance of arbitrary integer sequences, booleans in integer positions, empty strings and other duck-typed objects. It does not reinterpret these as different bytes. Private Python internals (`_power_cache`, `_join`, `_balance`, etc.) and mutation through reflection or `object.__setattr__` are not public port APIs. Runtime-specific repr, object hashes, serialization, memory errors and recursion limits are language adaptations, not byte/hash numerical promises. `Hashrope.VERSION` exposes the pinned version `"0.2.2"`; `Rope.ALPHA_NUM` and `Rope.ALPHA_DEN` expose the balance constants 2 and 7.

## Complexity and practical memory

The reference's tree algorithms are retained, but their mathematical operation counts are not fixed-time machine costs. BigInteger multiplication, folding and modular exponentiation depend on operand bit lengths; initial power-cache misses also have exponentiation cost.

- Whole-rope length/hash access reads stored metadata. Current-stream hashing reads a constant number of rope metadata values and composes them; it may need a cached or newly computed power.
- Balanced concat/split traverse and rebuild logarithmic tree paths in expanded leaf weight. Splitting a leaf also copies and hashes its byte slices, so arbitrary-size leaves add byte-proportional cost.
- Repetition adds one node and uses a number of modular operations proportional to the repetition count's bit length, plus the period-power calculation.
- A valid substring query follows boundary paths and combines stored interior hashes. Partial leaves must still be read and hashed. “Allocation-free substring” means no new rope nodes; byte slices and arithmetic/cache objects can allocate.
- Materialization takes time and storage proportional to expanded bytes. Height/validation traverse the represented node structure and are not constant-time metadata accessors.
- Sliding eviction bounds retained represented bytes by W under valid operations, but the hasher's exponent cache is not bounded by W. External references to old persistent snapshots keep those snapshots alive. Do not interpret the window bound as a hard cap on total JVM memory.

## Verification and preservation evidence

The committed fixtures contain 10,451 result records and 93 error records from the pinned wheel. SHA256 checks and expected counts guard fixture integrity. Deterministic property tests use independent byte-array/modular-arithmetic models across four parameter sets, including persistent snapshots and logical sizes above 2^100. Those fixture counts describe corpus contents; final passed/failed/skipped counts belong to the integrated verification report.

Strict TDD evidence records failing test runs, minimal implementations, passing runs and refactor reruns. A separate consumer smoke project exercises the packaged artifact. Main, sources and Javadoc JARs are built locally; this task does not publish to Maven Central or create a release tag.

The original `hashrope` checkout is read-only. Preservation evidence records tracked-file hashes, HEAD, index/config hashes and existing nested-repository dirtiness before and after work. Fixture generation, builds and caches occur outside it. The independent sibling `hashrope-java` repository contains the port in `java/`.
