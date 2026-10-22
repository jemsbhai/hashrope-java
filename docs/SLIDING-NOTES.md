# SlidingWindow compatibility notes

Reference: the SHA-256-pinned `hashrope-0.2.2-py3-none-any.whl`, `hashrope/sliding.py`.

All sizes, copy offsets, lengths, hashes, and positions in Java use `BigInteger`.
The no-argument constructor uses `d_max=32768`, `m_max=258`, `prime=2^61-1`, and `base=131`.
Capacity is exactly `d_max+m_max`, without a `long` overflow limit.

The window is a mutable streaming accumulator; its `window()` accessor returns an immutable rope snapshot.
Append operations replace the current root and preserve earlier roots. Byte arrays are copied into immutable leaves.
The Java object is not thread-safe; callers sharing an accumulator must synchronize their operations.
The capacity bounds represented retained bytes for valid operations, not total JVM allocation or the hash
calculator's unbounded exponent cache. Python uses the same unbounded cache behavior.

Eviction occurs only when the retained length is strictly greater than capacity. It evicts down to exactly `d_max`
bytes, adding the evicted length to the prefix length and composing its hash with the previous prefix hash.
At the capacity boundary, nothing is evicted. `m_max` and `d_max` determine retention; Python does not reject a
copy length exceeding `m_max` or a distance exceeding `d_max` while the source remains in the window.

Copies validate offset against decoded position first, then against retained window length. Their error strings
are preserved, mapped from Python `ValueError` to Java `IllegalArgumentException`. An offset of zero with a
positive copy length fails at division, mapped from Python `ZeroDivisionError` to Java `ArithmeticException`.
These failures leave the state unchanged. A zero-length copy still performs offset validation.

The ordinary copy domain is positive offset and nonnegative length. Python 0.2.2 does not reject negative offsets
or lengths: they can change `pos` while leaving the rope unchanged, breaking the streaming invariant. Java preserves
this observable release behavior rather than silently adding validation. For example, starting from `abc`, the call
`appendCopy(-1, 3)` leaves the rope and hash unchanged and changes `pos` from 3 to 6. A call `appendCopy(2, -1)`
changes `pos` from 3 to 2 with the same unchanged rope. These are compatibility cases, not supported encoding operations.

`appendBytes(null)` and `appendBytes(new byte[0])` are no-ops, matching Python's `if not data` treatment of `None`
and empty bytes. The hash functions `currentHash()` and `finalHash()` are equivalent and do not finalize or freeze the
accumulator; appends remain permitted afterwards.

Python exposes assignable state fields. Java preserves them as public assignable fields named `h`, `dMax`, `mMax`,
`W`, `hPrefix`, `lPrefix`, `rWindow`, and `pos`, alongside convenient read accessors. Assigning `dMax` or `mMax`
does not recompute `W`, exactly as in Python. Direct state mutation may violate the stream invariants; the API does
not silently normalize or reject such assignments.
