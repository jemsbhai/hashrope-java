# Independent final review

Reviewed the staged Java implementation against the downloaded PyPI Hashrope 0.2.2 source, including all production classes, Maven packaging, independent consumer tests, compatibility documentation and recorded differential/property evidence. No original checkout code was executed or modified, and this review made no production edits.

**Final reinspection: all four findings below are resolved in the staged source. No remaining blocking review findings.** The corrected implementations and their targeted green evidence were inspected after the fixes. The parent task's final integrated build and delivery checks remain separate verification steps.

The ordinary byte/hash algorithms showed no additional discrepancy: exact folding, signed arithmetic, BB[2/7] join/rotation choices, split clamping, malformed substring semantics, immutable structural sharing and normal sliding eviction follow the pinned implementation. Maven coordinates are `io.github.jemsbhai:hashrope:0.2.2`; the compiler targets release 17 and source/Javadoc JAR goals are bound. The separate consumer depends on the artifact coordinates rather than the library source tree. Final packaged-artifact checks remain the parent task's responsibility.

## Concrete findings sent to the implementation owners

### 1. Overridable helper dispatch differed from Python

After making PolynomialHash extensible, Java's RepeatNode constructor called `h.hashRepeat`, whereas Python directly calls `h.power`, `phi` and `mersenne_mul`. Java's repeated-range implementation made the same extra virtual call. Java `hashOverlap` delegated to overridable `hashRepeat` and `hashConcat`, while Python computes the primitive formula directly.

Trigger: subclass PolynomialHash and make `hashRepeat` throw or return zero. Constructing a repeated node, querying a spanning range of a repeated node or computing an overlap hash invokes the override in the original Java implementation but not in Python. This changes observable behavior even though the base-class oracle cases pass.

Correction requested: retain the pinned primitive operation sequence and property accesses. RepeatNode reads `prime` twice, after `power(child.len)`; repeated-range queries use the modulus captured at function entry. `hashOverlap` calls `power(d)` then `power(r)` and uses its private configured modulus. Corrected source was observed during review; the owners are recording dedicated red/green regressions.

Resolved: RepeatNode now calls power, phi and Mersenne multiplication directly with the two prime getter reads. Rope's repeated range uses the captured modulus and direct primitive calls. `hashOverlap` now computes its two powers in Python order and bypasses composite overrides. The inspected `evidence/tdd/08-subclass-dispatch-green/output.log` records three passing root regressions; `evidence/arithmetic/12-overlap-dispatch-green/output.log` records three passing dispatch regressions.

### 2. A hash override could mutate a Leaf's private payload

Java Leaf copied the input into its owned array, then passed that owned array to overridable `h.hash`. An override could retain the reference and mutate the supposedly immutable Leaf after construction while leaving the stored hash unchanged.

Trigger: `hash(byte[] data)` stores its argument in a field; after `new Leaf(new byte[]{1}, h)`, assign `h.retained[0] = 9`. Before the correction, `leaf.data()[0]` becomes 9 but its hash still represents byte 1.

Correction requested: copy first, then hash the original input, which is the Python ordering (`bytes(data)` stored before `h.hash(data)`). Corrected `h.hash(data)` source was observed during review. Root owns the capture/mutation regression.

Resolved: the constructor clones the caller's bytes, then hashes the caller's array. The capture/mutation regression confirms that changing the reference retained by an override leaves the Leaf payload and stored hash unchanged; it is included in the three passing root subclass tests above.

### 3. SlidingWindow callback evaluation order differed

Java's original `currentHash` expression evaluated the receiver `h` and argument `hPrefix` before calling overridable `windowLen`. Python evaluates its `window_len` property first, then reads the remaining state.

Trigger: override `windowLen` to assign `hPrefix = 42` and return the normal empty-window length. Python's equivalent property makes `current_hash()` return 42; the original Java expression returned zero.

Java's original inline `Rope.concat(rWindow, new Leaf(data,h), h)` also read `rWindow` before constructing the leaf. Python constructs the leaf first. A hash override that replaces the window root during hashing is therefore observed by Python and discarded by the original Java expression.

Correction requested: compute local window length and hash before invoking `h.hashConcat`; construct a local Leaf before reading `rWindow` for concat. Sliding implementation owner is recording targeted red/green tests.

Resolved: currentHash now reads local window length, then local window hash, then the mutable h/hPrefix state for composition. appendBytes now constructs a local Leaf before reading rWindow. Tests cover mutation of both hPrefix and h during windowLen, plus mutation of rWindow during leaf hashing. `evidence/sliding/14-callback-refactor-refactor/output.log` records 19 sliding tests passing with zero failures/errors/skips; source and tests were inspected in the final staged port.

### 4. Extreme modulus bit lengths overflowed the folding threshold

`BigInteger.ONE.shiftLeft(2 * bits)` multiplies two Java ints. For a positive modulus with bit length 1,073,741,824 (a representable approximately 128 MiB magnitude), `2 * bits` wraps to `Integer.MIN_VALUE`, and the computed threshold becomes zero. Even input value zero then remains in the folding loop forever. This is a static overflow finding; the review did not allocate the large modulus or run the nonterminating case.

Correction requested: avoid allocating the threshold. For a nonnegative value, `value >= 2^(2*k)` is exactly equivalent to `(long)value.bitLength() > 2L*k`; negative inputs must skip that loop. This keeps the arithmetic exact within BigInteger's supported magnitude and avoids both int overflow and an unnecessary giant allocation.

Resolved: the implementation uses the nonnegative bit-length predicate with long multiplication. The arithmetic owner additionally executed the actual large-modulus probe in an isolated JVM limited to 512 MiB. Inspected manifests in `evidence/arithmetic/14-extreme-modulus-{red,green}` record the old implementation being killed by a 10-second watchdog and the corrected implementation returning zero successfully in 0.343 seconds. Thus the originally static finding has now been reproduced and its correction executed without relying on a synthetic BigInteger subclass.

## Review limits

The existing differential and property evidence was inspected, not independently represented as a newly executed final build. Broad test counts do not cover arbitrary subclass callbacks; the concrete findings above demonstrate why those separate regressions matter. Allocation-free substring wording correctly means no new rope nodes, not zero temporary arithmetic/array objects. The documented typed-Java adaptations and deliberate rejection of nonpositive helper moduli remain explicit contract limits.

The parent task must record the final integrated test result after the corrections, build and inspect all three JARs, execute the separate consumer, verify source preservation and verify the pushed commit. No remaining concrete blocker was identified by this review.
