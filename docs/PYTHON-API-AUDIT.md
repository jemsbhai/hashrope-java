# Python Hashrope 0.2.2 compatibility audit

Audited 2026-10-22 from the extracted PyPI wheel, without importing or executing the original checkout. The release exposes 21 names in `hashrope.__all__`, plus `hashrope.__version__ == "0.2.2"`. This is an inventory and contract proposal, not a claim that the Java implementation has passed each row.

## Release and source mapping

- Registry: `https://pypi.org/pypi/hashrope/json`; project metadata identifies `https://github.com/jemsbhai/hashrope` as its source.
- Wheel: `hashrope-0.2.2-py3-none-any.whl`; SHA256 `710112e3aa14a991918a15331c1f418ce9d1c9d93c34a9da09e22be33c68a2d2`.
- Sdist: `hashrope-0.2.2.tar.gz`; SHA256 `881b367afc38b5d32609d3d604e4a19ce27e4eaeb3078e54948bdad69e54b499`.
- Original repository HEAD observed by parent and this audit: `100749d1cef9603465f4600b814c76d2ad6e9a6e`.
- Original Python location: `packages/python/hashrope`. The original is provenance context; the downloaded wheel is the executable compatibility oracle.

| Module | Wheel SHA256 | Local mapping |
|---|---|---|
| `__init__.py` | `93b9edb670de17512f4ce847f245360ca3ece7979b2f2bd5d50f892d180d5880` | Byte-identical |
| `polynomial_hash.py` | `5b8e4fae590ae0e262fdaed01dd4ce30566a62f4cd26b2eac635c8dd121d8c37` | Byte-identical |
| `rope.py` | `55127ade6945778f0073e5d78ae8ae00c1a73d829ecb665bb1d1265b528415a4` | Local SHA256 `f436c67bdda482040f59d0082b42df2f450482c8f796f0fa06e07b1c3f3ade48`; only three docstrings differ |
| `sliding.py` | `5682a99b1bf297b52acc230a81068c1a2f0f96dd25e84b9b32a1eee1328eff9f` | Byte-identical |

The three local `rope.py` docstring additions explicitly describe byte offsets in `rope_split`, `rope_substr_hash`, and `rope_from_bytes`, including splits inside a UTF-8 sequence. Executable statements are unchanged.

## Cross-cutting contract

1. Data is a byte sequence, not Unicode text. Hashing adds one to each unsigned byte in `[0,255]`; NUL therefore contributes one and `0xff` contributes 256. No encoding, decoding, normalization, code-point or grapheme operation exists. Any Java String convenience must choose an explicit encoding and explain that indices remain byte indices.
2. All arithmetic values, lengths, weights, counts, repetition factors and positions are arbitrary-precision Python integers. Java `long` alone cannot provide full parity. BigInteger is needed for compressed ropes longer than `Long.MAX_VALUE`, their splits and range queries, large counts, 127-bit residues, and intermediate products. Leaf materialization and `byte[]` necessarily have Java array-size limits; these should raise a documented error before truncation or overflow.
3. Empty rope is `None`. Empty bytes hash to zero. Null/empty identity cases should be preserved explicitly.
4. Nodes are immutable frozen slotted dataclasses. Leaf input is copied to immutable bytes. Operations share unchanged subtrees; null concatenation, repetition by one, boundary splits, and exact internal splits return original references. Hash objects have a mutable internal exponent cache. SlidingWindow is mutable.
5. A node stores no hash parameter identifier. The caller must consistently use the same prime/base across construction and operations. The Python implementation does not reject mixed contexts and the validator does not detect them.
6. No predeclared parameter profiles exist. The public parameters are Mersenne `prime` and `base`; constants provide 61- and 127-bit choices. Defaults are `prime=2**61-1`, `base=131`. Useful test profiles include `(2**61-1,131)`, `(2**127-1,131)`, `(31,2)`, `(31,30)`, `(3,2)`, and default-prime large bases. All Mersenne-form moduli can be mechanically processed, although the documented mathematical contract asks for a Mersenne prime.
7. Python annotations do not enforce input types. Integer sequences, booleans, mutable fields, malformed nodes, negative substring coordinates and non-Mersenne moduli expose accidental behavior. The Java contract must mark any deliberate rejection or inability to represent these; these must not be silently described as complete unrestricted parity.
8. Node equality is structural dataclass equality, including type, payload, child structure, hash, length, weight and repetition count. Equal byte content with different tree shapes is not equal. Dataclasses also provide structural hashing and repr; Java object hash codes and textual repr need an explicit language-adaptation statement if different. PolynomialHash and SlidingWindow use ordinary object identity equality.

## Arithmetic API matrix

Line references are to files inside the wheel.

| Python symbol | Source | Defined behavior and required tests |
|---|---|---|
| `MERSENNE_61` | `polynomial_hash.py` constant | Exact `2305843009213693951` |
| `MERSENNE_127` | same | Exact `170141183460469231731687303715884105727` |
| `mersenne_mod(a,p=MERSENNE_61)` | lines 25–47 | Nonnegative `a`, Mersenne-form `p`; repeated bit folds for arbitrarily large `a`, two final folds, one conditional subtraction. Test zero, `p-1`, `p`, `p+1`, `p*p`, values beyond 256 bits and both advertised primes. Do not use overflowing fixed-width multiplication. |
| `mersenne_mul(a,b,p=MERSENNE_61)` | 50–57 | Calls `mersenne_mod(a*b,p)` using exact product; tests near residue limits and huge operands. |
| `phi(q,alpha,p=MERSENNE_61)` | 64–112 | Inverse-free geometric sum. `q=0` returns 0; `q=1` returns 1 without normalizing by `p`. Binary left-to-right doubling. Test alpha 0, 1, p-1, p, >p, q zero/one/even/odd/very large and the `alpha == 1` degeneracy. |
| `PolynomialHash(prime=MERSENNE_61,base=131)` | 138–143 | Rejects `base<2` or `base>=prime` with `ValueError("Base must be in [2, p-1], got {base}")`. Initializes cache `{0:1,1:base}`. No primality or Mersenne-form validation. |
| `.prime`, `.base` | 146–151 | Read-only properties exposing exact chosen integers. |
| `.power(n)` | 153–164 | Exact modular exponentiation with cache. Zero yields 1, one yields base, repeated queries identical. Negative exponents are supported through Python modular inversion; non-invertible base/modulus raises ValueError. Enormous exponents are supported in logarithmic exponent length. |
| `.hash(data)` | 166–191 | Horner polynomial `h = fold(h*base + unsigned_byte + 1)`, empty => 0. Test every byte, all-zero inputs, UTF-8, arbitrary binary data. |
| `.hash_concat(h_a,len_b,h_b)` | 193–200 | `fold(h_a * power(len_b) + h_b)`. Test empty operands, large lengths, compositional identity, and nontrivial high residues. |
| `.hash_repeat(h_s,d,q)` | 202–210 | `mersenne_mul(h_s,phi(q,power(d)))`; computes `power(d)` even for q=0. Test q=0/1 and huge q, d=0, geometric degeneracy. |
| `.hash_overlap(h_p,d,l,h_prefix)` | 212–241 | Uses Python `divmod(l,d)` and combines q whole periods with prefix length r. Test l<d, l=d, l>d, r=0, r>0, zero l. `d=0` raises ZeroDivisionError even when l=0. Caller supplies correct prefix hash; no length/data verification occurs. |

Additional observed arithmetic outside documented input domains:

- `PolynomialHash(17,2)` and `(8,2)` are accepted, but folding with non-Mersenne bit masks does not generally compute `% prime`. Replacing folding by BigInteger.mod is equivalent only for supported positive Mersenne-form moduli and nonnegative arithmetic.
- `phi(-5,2,31) == 3`; negative q is not checked, and Python `int.bit_length()` on negatives differs from BigInteger.bitLength(). A mechanical port must use absolute bit length to mimic this accidental behavior.
- Small negative `mersenne_mod` inputs happen to normalize, but `mersenne_mod(-(2**200),31)` remains negative after the two final folds. The routine documents nonnegative inputs.
- `hash([256]) == 257`, `hash([-1]) == 0`; arbitrary integer sequences are not bytes yet are accepted. `hash('') == 0`, while a nonempty str raises TypeError. A byte[] Java API naturally excludes these inputs; document the adaptation.
- Python division/remainder for negative values is floor division, unlike Java BigInteger.divide/remainder. This matters only if reproducing out-of-domain negative dimensions/counts.

## Node and rope API matrix

| Python symbol | Source (`rope.py`) | Defined behavior and required tests |
|---|---|---|
| `Node` | type alias | `Optional[Leaf | Internal | RepeatNode]`; not a constructible runtime node type. Java can express it as an interface/class with null empty value. |
| `Leaf(data,h)` | 55–74 | Nonempty data only. Empty raises `ValueError("Leaf cannot be empty")`. Copies bytes; public read-only fields `data`, `hash_val`, `len`, `weight`; weight always one regardless of byte length. Test input mutation isolation and defensive output handling in Java. |
| `Internal(left,right,h)` | 78–105 | Public immutable `left`, `right`, `hash_val`, `len`, `weight`. Length/weight sums and compositional hash. Constructor does not rebalance or enforce non-null children; Python null child fails AttributeError. A deliberately unbalanced direct node is constructible and validator must detect it. |
| `RepeatNode(child,reps,h)` | 109–140 | Reject reps<2 with `ValueError("RepeatNode reps must be >= 2, got {reps}")`, before touching child. Public immutable `child`, `reps`, `hash_val`, `len`, `weight`; counts multiply exactly. Nested repeat nodes remain nested; no flattening is performed. Child null with valid reps fails AttributeError. |
| `rope_len(node)` | 148–150 | Null => 0; otherwise stored byte length. |
| `rope_hash(node)` | 153–155 | Null => 0; otherwise stored hash, without rehashing or context validation. |
| `rope_height(node)` | 158–167 | Null and Leaf => 0; Repeat => child height+1; Internal => max child height+1. This is structural height, not logarithm of expanded length or weight. |
| `rope_concat(left,right,h)` | 294–350 | Null identities return the other reference. Nonempty concatenation uses BB[2/7] join and rotations. Weight is expanded leaf count, not byte count. Test one enormous leaf vs many tiny leaves; append/prepend; highly unequal repeats; alternating/nested repeats; data/hash/length preservation and balance at every internal. |
| `rope_split(node,pos,h)` | 357–442 | Null => (null,null). pos<=0 => (null,same node); pos>=length => (same node,null). Interior leaf split copies exact byte slices. Internal boundary returns original child references. Repetition split handles aligned q boundaries and inside-period residues. Test every byte boundary including inside UTF-8 and beyond long range in compressed ropes. |
| `rope_repeat(node,q,h)` | 452–466 | q=0 => null; q=1 => same node; otherwise RepeatNode. Negative q errors as constructor. For null node, q=0/1 returns null; q>=2 fails AttributeError. O(1) added node; geometric hash uses O(log q) iterations. |
| `rope_substr_hash(node,start,length,h)` | 474–568 | Null or length=0 => 0. For valid 0<=start, 0<=length, start+length<=rope_len: exact substring byte hash. A full node range returns stored metadata; partial range descends boundaries and combines full interior metadata. Repeat spans use tail + whole repetitions + head. No rope nodes created; leaf slices and power-cache entries can allocate ordinary objects. |
| `rope_to_bytes(node)` | 576–593 | Null => empty bytes. Leaves collected left-to-right; Repeat recursively expands each repetition. Time/memory proportional to expanded bytes, no configured cap. Java byte[] maximum needs explicit checked limit and a streaming alternative can be additive. |
| `rope_from_bytes(data,h)` | 601–605 | Empty => null; otherwise exactly one Leaf, no automatic chunking. |
| `validate_rope(node,h=None)` | 613–654 | Returns None on success, AssertionError on invariant violations, TypeError on unknown node types. Checks leaf weight/data length, internal length/weight/BB[2/7], repeat length/weight/reps, recursively. `h` is accepted but unused. Does NOT recompute any hashes, test leaf nonemptiness independently, check profiles, or validate arbitrary object mutation. Java assertions disabled by default must not silently disable validation if this is ported as a public check. |

BB[2/7] details: for internal total weight >2, `2*total <= 7*leftWeight <= 5*total`. The arithmetic must not overflow. For total<=2, balance check accepts immediately. `_join` descends the heavier subtree; repeat nodes decompose by halving repetitions. `_balance` uses a single rotation only if both newly formed pairings satisfy the condition; otherwise recursively balances parts from a double decomposition. Copy the pinned checks, not a generic AVL or earlier approximate balancing implementation, when structural height parity matters.

### Substring inputs outside the valid range

There is no explicit bounds check. Leaf paths inherit Python slicing; Repeat paths can extend the mathematical periodic sequence past the actual rope; Internal paths can multiply by requested lengths that exceed available data. Different representations of the same content can therefore return different results outside the valid domain. Concrete observations, base 131 / Mersenne-61:

| Input | Leaf b'abcdef' | Internal(Leaf b'abc', Leaf b'def') | Repeat(Leaf b'ab', 3) |
|---|---:|---:|---:|
| start=-1,length=1 | 0 | 0 | 99 |
| start=-8,length=4 | 12937 | 0 | 222024794 |
| start=0,length=-1 | 29085282700 | 12937 | 1993167763282484272 |
| start=0,length=9 | 3810172033803 | 1648080503205884880 | 1648074243832573153 |
| start=6,length=1 | 0 | 0 | 98 |
| start=9,length=0 | 0 | 0 | 0 |

A Java port should either reproduce and fixture these behaviors or explicitly list stricter bounds checks as an intentional deviation. They cannot be treated as a coherent Python slicing contract for ropes.

## SlidingWindow API matrix

| Symbol | Source (`sliding.py`) | Behavior and tests |
|---|---|---|
| `SlidingWindow(d_max=32768,m_max=258,prime=MERSENNE_61,base=131)` | 66–87 | Checks d_max>=1 first, then m_max>=1. Exact messages `d_max must be >= 1, got {d_max}` / `m_max must be >= 1, got {m_max}`. Then creates PolynomialHash. W=d_max+m_max. Empty state h_prefix=l_prefix=pos=0, r_window=None. |
| public state fields | slots and constructor | `h`, `d_max`, `m_max`, `W`, `h_prefix`, `l_prefix`, `r_window`, `pos` are public and writable in Python; Java read-only access is a deliberate adaptation if chosen. No automatic consistency restoration if caller edits them. |
| `.window_len` | 90–92 | Current rope byte length; read-only property; can range from zero through W. |
| `.current_hash()` | 94–101 | Concatenates h_prefix with current window hash using current window length; tests after every append and eviction, including empty stream. |
| `.final_hash()` | 103–109 | Exact alias behavior for current_hash; does not finalize, reset, or prevent later writes. |
| `.append_bytes(data)` | 111–125 | Empty/falsy => no-op. Nonempty creates one leaf, joins, increments pos by length then evicts. Mutable input copied by Leaf. No maximum literal chunk length. |
| `.append_copy(offset,length)` | 127–184 | Validate `offset>pos` first, then `offset>window_len`; both even for length zero. Nonoverlap offset>=length extracts by two splits. Overlap offset<length repeats offset-byte pattern and appends remainder. Increment pos by length and then evict. Test offset=length, offset=1, exact/nonexact multiples, copied data crossing leaf/repeat boundaries, and copies after eviction. |
| eviction behavior | 186–210 | If window_len<=W: no eviction. If window_len>W: split off window_len-d_max bytes, fold their hash into prefix, increase l_prefix by excess, retain exactly d_max bytes. Test W-1, W, W+1 and huge literal/copy jumps. |

Copy error messages:

- `ValueError("Invalid copy: offset {offset} exceeds decoded length {pos}")`.
- `ValueError("Copy offset {offset} exceeds window size {win_len}. Increase d_max (currently {d_max}) to at least {offset}.")`.
- Offset zero, positive length reaches Python division by zero and raises `ZeroDivisionError("integer division or modulo by zero")`; state remains unchanged.

The names d_max and m_max do **not** impose per-operation caps. An offset greater than d_max is accepted if those bytes remain in the window. A copy length greater than m_max is accepted and evicts afterward. d_max+m_max is an eviction threshold only.

Zero-length copies still check offset upper bounds. `(offset,length)=(0,0)` is a no-op even on an empty stream. Negative offsets and/or lengths are not rejected and can desynchronize pos from actual represented bytes: after b'abc', `append_copy(1,-1)` makes pos=2 while window length remains 3; `append_copy(-1,3)` makes pos=6 while bytes remain b'abc'. These are accidental out-of-domain behaviors, not valid streaming operations.

## Required verification coverage

- One row per public symbol, method/property and node/state field, with Java mapping, test references and a status that distinguishes exact numeric behavior, language adaptation, intentional deviation, and untested.
- Golden fixtures generated exclusively from the pinned wheel; encode arbitrary integers as decimal strings and bytes as hex/base64, not lossy JSON numeric values.
- Cross-profile fixture cases for all 256 bytes, zero bytes, UTF-8 multibyte text, empty values, boundary hashes, huge powers/counts, nested repetitions, persistent references, tree shape/height, split clamps, every valid substring boundary and sliding transitions/error ordering.
- Randomized differential operation histories with deterministic seeds. Store/reuse earlier roots and verify that later updates never alter their bytes, metadata or hashes.
- Independent properties: concat associativity at byte/hash level, split/rejoin, substring hash equals direct hash, repeat equals explicit small expansion, polynomial concatenation/repetition/overlap identities, sliding hash equals decoded reference bytes after each operation, and BB[2/7] on all constructed internal nodes.
- Large compressed tests must avoid `rope_to_bytes`: lengths, weights, hashes, aligned and unaligned splits, and substring queries above 2**63 should work if claiming arbitrary-integer parity.
- Full-node substring fast path should be verified without relying only on output correctness; instrument test hasher calls or otherwise verify no leaf traversal/reconstruction. Allocation-free here means no new rope nodes, not literally no allocation anywhere.
- Representation limits: Java array sizes, heap/stack availability, JVM object identity/hash/repr conventions, and exception type mapping. Do not introduce a signed-long count ceiling without listing it as a parity gap.

## Audit evidence and preservation

`probe_edges.py` and `probe_edges.jsonl` in the task workspace contain reproducible downloaded-wheel arithmetic/sliding probes; Python executed with `-B` and `PYTHONDONTWRITEBYTECODE=1`. Additional direct probes confirmed range-shape differences, dataclass equality/frozen behavior, bytearray copying, split/reference identities and mixed-context validator acceptance.

A local preservation baseline records SHA256 hashes for 748 original and nested-repository files, including existing untracked work, plus six Git metadata files without configuration contents. The final comparison confirms unchanged files, HEADs, branches, indexes, configuration and dirty statuses. Detailed paths and unrelated work inventory remain local; only the [aggregate preservation result](../evidence/original-preservation-final.json) is published. Git reads disabled optional locks. No source, branch, index or configuration writes were performed by this audit.
