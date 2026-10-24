# Hashrope Java API parity matrix

This matrix covers every name in `hashrope.__all__`, public class methods/properties/fields, the module version and the rope module's balance constants. It records the implementation contract and named verification rather than treating the existence of an API as proof of parity. See [COMPATIBILITY.md](COMPATIBILITY.md) for details, assumptions and runtime limits.

**Historical 0.2.2 verification:** 10,608 library JUnit tests passed (including 3,868 arithmetic assertions), plus 2 independent consumer tests; zero failures, errors or skips. All 10,544 golden/error records are exercised. See [VERIFICATION.md](VERIFICATION.md).

## Evidence key

- **A**: [`PolynomialHashTest`](../java/src/test/java/hashrope/PolynomialHashTest.java), nine executable arithmetic groups. [`ApiTest.arithmeticContractRunsUnderMaven`](../java/src/test/java/hashrope/ApiTest.java) calls `runAll()` from JUnit so Maven executes them.
- **R**: [`RopeTest`](../java/src/test/java/hashrope/RopeTest.java), named node/rope contracts below.
- **S**: [`SlidingWindowTest`](../java/src/test/java/hashrope/SlidingWindowTest.java), named mutable-stream contracts below.
- **D**: [`DifferentialTest`](../java/src/test/java/hashrope/DifferentialTest.java): `publishedPythonGoldenResults`, `publishedPythonErrors`, and `fixtureFilesAreThePinnedOracleAndEveryRowIsPresent`.
- **P**: [`PropertyTest`](../java/src/test/java/hashrope/PropertyTest.java), independent deterministic reference models.
- **API**: [`ApiTest.releaseAndBalanceConstantsMatchPinnedPythonModule`](../java/src/test/java/hashrope/ApiTest.java).
- **X**: [`SubclassParityTest`](../java/src/test/java/hashrope/SubclassParityTest.java) and [`PolynomialHashDispatchTest`](../java/src/test/java/hashrope/PolynomialHashDispatchTest.java), primitive dispatch order and owned-array isolation; sliding callback order is covered by S.
- **T**: [`SubstringTraversalTest`](../java/src/test/java/hashrope/SubstringTraversalTest.java), counting-hasher tests for metadata fast paths and boundary traversal.

Golden result rows are generated exclusively from the SHA256-pinned PyPI wheel. Decimal integers are parsed as BigInteger and byte fields as hex. D also checks lengths, weights, structural heights, bytes and invariant validation for rope/split records, and the complete observable stream state for streaming records. Expected errors map Python exception categories to Java ones; explicit library error messages are compared exactly. VM-dependent null/division diagnostics are not required to match word for word.

## Top-level exported API

| Python export | Java mapping | Contract / status | Verification |
|---|---|---|---|
| `PolynomialHash` | `PolynomialHash` | Mapped; all methods listed below | A; D arithmetic rows |
| `phi` | `PolynomialHash.phi` | Exact inverse-free algorithm including signed-count quirks; nonpositive-modulus exception noted below | A.geometricAccumulator, A.nonpositiveModuliAreRejected; D `phi` |
| `mersenne_mod` | `PolynomialHash.mersenneMod` | Exact positive-modulus folding, including negative operands/non-Mersenne masks | A.reductionAndMultiplication, A.pythonFoldingCompatibility; D `mod` |
| `mersenne_mul` | `PolynomialHash.mersenneMul` | Exact arbitrary-precision product followed by reference folding | A.reductionAndMultiplication; D `mul` |
| `MERSENNE_61` | `PolynomialHash.MERSENNE_61` | Exact `2^61-1` BigInteger | A.defaultsAndBytes; D default profile |
| `MERSENNE_127` | `PolynomialHash.MERSENNE_127` | Exact `2^127-1` BigInteger | A.defaultsAndBytes; D 127-bit profile |
| `Leaf` | `Leaf` | Immutable owned bytes, weight one, empty-leaf error | R.leafStoresUnsignedBytesAndImmutableMetadata, R.leafRejectsEmptyAndNull; D `rope` / `leaf` errors |
| `Internal` | `Internal` | Immutable direct join; constructor deliberately does not rebalance | R.internalNodeAndRepetitionMetadata, R.validatorChecksBalanceAndProfileWithoutRehashing; D `rope`, `internal` / `validate` errors |
| `RepeatNode` | `RepeatNode` | Immutable repeated child; reps>=2, exact len/weight/hash; nested repeats preserved | R.internalNodeAndRepetitionMetadata, R.repeatsRetainSharingAndUnboundedLogicalSizes; D `rope`, `repeatNode` errors |
| `Node` | Sealed `Node` interface, with null empty value | Java type adaptation of Optional union; no arbitrary node subclasses | R.emptyRopeAndAccessors; D empty-node cases |
| `rope_concat` | `Rope.concat` | BB[2/7] persistent join with null identity | R.concatenationAndSplitsPreserveReferencesAndBytes, R.repeatedAppendAndPrependMaintainWeightBalance, R.balancingHandlesRepeatDecompositionAndPersistentSplitHistories; D `rope`; P.seededEditTraces |
| `rope_split` | `Rope.split`, returning `Rope.Split` | Byte offset; clamps endpoints; shared roots/children; exact huge positions | R.concatenationAndSplitsPreserveReferencesAndBytes, R.repeatSplitsHandleBoundariesInsidePeriodsAndUnboundedPositions; D `split`; P.arbitraryPrecisionRepeatsAndSplits |
| `rope_repeat` | `Rope.repeat` | q=0 null, q=1 identity, otherwise RepeatNode; no expansion | R.repeatsRetainSharingAndUnboundedLogicalSizes; D `rope` / `repeat` errors; P.arbitraryPrecisionRepeatsAndSplits |
| `rope_substr_hash` | `Rope.substrHash` | Valid byte ranges exact; full coverage metadata fast path; malformed ranges preserve Python behavior | R.substringHashMatchesEveryValidRangeAcrossAllNodeKinds, R.substringFastPathUsesStoredMetadataAndHandlesHugeRepeats, R.outOfDomainSubstringInputsRetainPythonShapeDependentResults; D `substr`; P.byteOwnershipAndSubstringQueriesPreserveExistingGraph; T |
| `rope_len` | `Rope.len` | Null→zero; exact stored BigInteger byte length | R.emptyRopeAndAccessors; D `rope` / `split`; P arbitrary-size cases |
| `rope_hash` | `Rope.hash` | Null→zero; stored hash without recomputation | R.emptyRopeAndAccessors; D `rope` / `split`; P independent metadata checks |
| `rope_height` | `Rope.height` | Null/leaf→0, repeat child+1, internal max child+1 | R.internalNodeAndRepetitionMetadata; D `rope` / `split` / `sliding` structural heights |
| `rope_from_bytes` | `Rope.fromBytes` | Empty→null; nonempty→one Leaf | R.emptyRopeAndAccessors; P.seededEditTraces |
| `rope_to_bytes` | `Rope.toBytes` | Fresh exact byte[]; deliberate checked materialization limit | R.emptyRopeAndAccessors, R.internalNodeAndRepetitionMetadata, R.repeatsRetainSharingAndUnboundedLogicalSizes; D small materialized results; P arbitrary-size failure |
| `validate_rope` | `Rope.validate(node)` / `Rope.validate(node,h)` | Structural checks and messages; supplied h checks profile; hashes not recomputed; explicit AssertionError | R.validatorChecksBalanceAndProfileWithoutRehashing; D `validate` errors; P independent invariants |
| `SlidingWindow` | `SlidingWindow` | Mutable state and operations mapped below | S; D `sliding`; P.seededStreamingTraces |

Additional public metadata:

| Python name | Java mapping | Verification |
|---|---|---|
| `hashrope.__version__` | `Hashrope.VERSION`, value `"0.3.4"` | API |
| `hashrope.rope.ALPHA_NUM` | `Rope.ALPHA_NUM`, value 2 | API; BB[2/7] invariant tests |
| `hashrope.rope.ALPHA_DEN` | `Rope.ALPHA_DEN`, value 7 | API; BB[2/7] invariant tests |

## PolynomialHash constructors, properties and methods

| Python member | Java mapping | Cases / named checks |
|---|---|---|
| `PolynomialHash()` | `new PolynomialHash()` | Default prime/base; A.defaultsAndBytes |
| `PolynomialHash(prime,base)` | `(BigInteger,BigInteger)` and `(long,long)` constructors | Exact base interval; valid low/high bases; invalid 1, p and negative; D constructor errors |
| Optional prime/base arguments | Explicit full constructor for nondefault pair | Same selected parameters; immutable HashProfile identity |
| `.prime` | `.prime()` | Read-only exact modulus; A.defaultsAndBytes |
| `.base` | `.base()` | Read-only exact base; A.defaultsAndBytes |
| `.power(n)` | `.power(BigInteger)` / `.power(long)` | Zero/one, cached repeat, huge positive/negative exponent, invertible/noninvertible negative exponent; A.powers; D `power` / power errors |
| `.hash(data)` | `.hash(byte[])` | Empty, NUL offset, all unsigned bytes, UTF-8, random binary input; A.defaultsAndBytes; D `hash` |
| `.hash_concat(a,len_b,b)` | `.hashConcat(BigInteger,BigInteger,BigInteger)` / long middle argument | Empty concatenation, large/signed lengths, random composition; A.concatenation; D `concatHash` |
| `.hash_repeat(h,d,q)` | `.hashRepeat(BigInteger,BigInteger,BigInteger)` / long d,q | q=0/1, explicit expansions, huge q, alpha=1 degeneracy; A.repeatedHashes; D `repeatHash` |
| `.hash_overlap(h,d,l,prefix)` | `.hashOverlap(BigInteger,BigInteger,BigInteger,BigInteger)` / long d,l | Zero/partial/full/repeated periods, exact supplied prefix, d=0 error, signed floor division; A.overlappingHashes; D `overlapHash` / overlap errors |
| Top-level helper default modulus | Static helper overload omitting prime | Default Mersenne-61; A.reductionAndMultiplication, A.geometricAccumulator |

The cache is an internal implementation detail, not a public API. The Java concurrent map is a language implementation choice; cache identity/order is not compared with Python.

## Node fields and value behavior

| Python field or behavior | Java mapping | Verification |
|---|---|---|
| `Leaf.data` | `Leaf.data()` returns a copy | R.leafStoresUnsignedBytesAndImmutableMetadata; P.byteOwnershipAndSubstringQueriesPreserveExistingGraph |
| Every node `.len` | `Node.len()` | Exact BigInteger stored/derived length; R metadata tests; D rope/split records |
| Every node `.weight` | `Node.weight()` | Expanded leaf count, not byte count; R metadata tests; P.byteLengthsDoNotDetermineTreeWeights |
| Every node `.hash_val` | `Node.hashVal()` | Stored BigInteger hash; R metadata tests; D, P independent hash checks |
| `Internal.left` / `.right` | `.left()` / `.right()` | Shared immutable children; R.internalNodeAndRepetitionMetadata, R.concatenationAndSplitsPreserveReferencesAndBytes |
| `RepeatNode.child` | `.child()` | Shared child, including nested repeat; R.repeatsRetainSharingAndUnboundedLogicalSizes |
| `RepeatNode.reps` | `.reps()` | Exact BigInteger; q above 2^100; R, P large-repeat tests |
| Frozen attribute writes | Final classes/private final state/read-only accessors | Static type/design adaptation plus defensive-array tests; no public setters |
| Dataclass equality | Structural `.equals` | R.leafEqualityIsStructuralAndContentBased, R.internalNodeAndRepetitionMetadata |
| Dataclass hash/repr | Java `.hashCode` / `.toString` | Deliberate Java representation; equality-compatible hashCode; R.leafEqualityIsStructuralAndContentBased |
| Split tuple unpacking | `Rope.Split.left()` / `.right()` | Java record adaptation; all split tests |

Java-only conveniences are additive: `Leaf(byte[])` uses default hash parameters; `RepeatNode` accepts a long repetition count; `Rope.repeat`, `split` and `substrHash` provide long overloads. BigInteger forms are not limited to signed-long logical sizes; the documented JVM magnitude and allocation limits still apply.

## SlidingWindow fields, properties and methods

| Python member | Java mapping | Verification |
|---|---|---|
| Constructor defaults | `new SlidingWindow()` | S.defaultsAndEmptyStateMatchPython |
| Constructor d_max,m_max | `(long,long)` / `(BigInteger,BigInteger)` | S.constructorValidationKeepsPythonOrderAndMessages, S.customProfilesAndArbitraryPrecisionCapacityArePreserved, S.convenienceConstructorsPreserveTheSameProfiles |
| Constructor all parameters | Four BigInteger values; long-capacity/BigInteger-parameter and all-long conveniences | S.customProfilesAndArbitraryPrecisionCapacityArePreserved, S.convenienceConstructorsPreserveTheSameProfiles; D streaming constructor errors |
| `.h` | Mutable public `.h`; accessor `.h()` | S.publicStateAssignmentsRemainObservableLikePythonSlots |
| `.d_max` | Mutable `.dMax`; accessor `.dMax()` | Same; eviction and direct-retention-change tests |
| `.m_max` | Mutable `.mMax`; accessor `.mMax()` | Same; constructor/default tests |
| `.W` | Mutable `.W`; accessor `.capacity()` | S.publicStateAssignmentsRemainObservableLikePythonSlots, S.aLargeSingleAppendEvictsAndDirectCapacityAssignmentsTakeEffect |
| `.h_prefix` | Mutable `.hPrefix`; accessor `.hPrefix()` | S state assignment/eviction tests; D complete stream state |
| `.l_prefix` | Mutable `.lPrefix`; accessor `.lPrefix()` | Same; D complete stream state |
| `.r_window` | Mutable `.rWindow`; accessor `.window()` | S state assignment/snapshot tests; D window bytes/hash/height/weight |
| `.pos` | Mutable `.pos`; accessor `.pos()` | S arbitrary-precision/negative-quirk tests; D complete stream state |
| Read-only `.window_len` | `.windowLen()` | S defaults/eviction tests; D complete stream state |
| `.current_hash()` | `.currentHash()` | S literal/copy/eviction tests; D `sliding`; P.seededStreamingTraces |
| `.final_hash()` | `.finalHash()` | Same current value; no finalization barrier; overridable currentHash dispatch; S.finalHashDispatchesToAnOverriddenCurrentHashLikePython, S literal tests; D `sliding`; P.seededStreamingTraces |
| `.append_bytes(data)` | `.appendBytes(byte[])` | S.literalAppendsComposeBytesAndKeepImmutableSnapshots, S.nullAndEmptyLiteralAppendsAreNoOps; D `sliding` |
| `.append_copy(offset,length)` | `.appendCopy(BigInteger,BigInteger)` / `.appendCopy(long,long)` | S.nonoverlappingCopiesUseByteOffsetsAndPreserveEarlierRoots, S.overlappingCopiesRepeatPatternsAndAppendPartialTails; D streaming traces/errors; P.seededStreamingTraces |
| Threshold/eviction | Internal implementation, observed through fields/methods | S.evictionOccursStrictlyAboveCapacityAndFoldsThePrefix, S.aLargeSingleAppendEvictsAndDirectCapacityAssignmentsTakeEffect |
| d_max/m_max are not per-operation caps | Same acceptance | S.copyLengthsAndDistancesAreNotArtificiallyCappedByCapacityParameters |
| Huge compressed copy | Same BigInteger counter/metadata behavior | S.enormousCopiesKeepArbitraryPrecisionPositionsWithoutMaterializing |
| Copy error order/state | Decoded bound, then retained bound; zero-offset division failure | S.invalidCopyBoundsKeepPythonMessagesOrderingAndState; D `slidingCopy` errors |
| Negative offset/length quirks | Preserved, including inconsistent pos | S.releaseNegativeInputQuirksArePreservedRatherThanSilentlyRejected; D `quirks` traces |

## Edge behavior and explicit limits

| Area | Compatibility decision | Verification or reason |
|---|---|---|
| Unsigned bytes / zero-padding distinction | Exact coefficients byte+1 | A.defaultsAndBytes; R unsigned leaf test; D all-byte fixtures |
| UTF-8 / byte indices | Explicit encoding, exact byte splits even within a character | R.unicodeIsExplicitlyEncodedAndSplitByBytes; P ownership/query graph test; D UTF-8 data |
| Overflow / >long logical size | BigInteger throughout metadata/count arithmetic | R huge repeats/splits; S huge capacities/copies; P.arbitraryPrecisionRepeatsAndSplits |
| `toBytes` outside int range | Deliberate ArithmeticException, no truncation | R.repeatsRetainSharingAndUnboundedLogicalSizes; P.arbitraryPrecisionRepeatsAndSplits |
| BigInteger supported magnitude | JDK reference range excludes endpoints +/-2^Integer.MAX_VALUE; arithmetic/allocation failures can occur at implementation limits | Explicit [representation boundary](COMPATIBILITY.md#bytes-integer-sizes-and-ownership); no parity claim beyond it |
| Heap/stack exhaustion | JVM resource limits remain; not numerically equivalent to Python limits | Documented representation boundary; not tested through deliberate resource exhaustion |
| Hash context mismatch | Not rejected; stored metadata kept | R.validatorChecksBalanceAndProfileWithoutRehashing |
| Validator hash verification | Deliberately absent, matching Python | Same; P separately verifies hashes independently |
| Negative and oversized split positions | Clamp endpoints | R concat/split test; D split fixtures; P.seededEditTraces |
| Invalid substring bounds | Preserve shape-dependent Python slicing/periodic behavior | R.outOfDomainSubstringInputsRetainPythonShapeDependentResults; D `substr` |
| Substring full coverage | Return stored hash; no leaf rehash | R.substringFastPathUsesStoredMetadataAndHandlesHugeRepeats; T counting hasher |
| No new rope nodes in substring | Query-only traversal; persistent graph unchanged | Source implementation review; P.byteOwnershipAndSubstringQueriesPreserveExistingGraph; T boundary-call counts. Ordinary byte/BigInteger/cache allocation is allowed |
| p<=0 helper arguments | Intentional IllegalArgumentException to avoid reference nontermination | A.nonpositiveModuliAreRejected; excluded from Python generator |
| `phi` q=0/1 with p<=0 | Short-circuit zero/one preserved | A.nonpositiveModuliAreRejected |
| Positive non-Mersenne parameters | Reference bit-folding behavior retained | A.pythonFoldingCompatibility; D non-Mersenne records |
| Negative arithmetic / phi / overlap dimensions | Reference folding/absolute bit length/floor division retained | A.pythonFoldingCompatibility, A.geometricAccumulator, A.overlappingHashes; D arithmetic quirks |
| Python integer sequences, strings, bools and custom duck types | Java's byte[]/BigInteger signatures exclude them | Static language adaptation; no implicit conversion |
| Hasher/stream subclassing | PolynomialHash and SlidingWindow remain extensible; normal Java method overrides | X primitive dispatch/ownership; T counting hasher; S callback order and finalHash override tests |
| Python arbitrary node subclasses/monkeypatches | Sealed/final Java node types; no runtime monkeypatching | Static language adaptation; private implementation APIs are not exported |
| Public sliding slots | Mutable camelCase fields retained, no auto-invariant repair | S.publicStateAssignmentsRemainObservableLikePythonSlots |
| Object hash/repr | Java hashCode/toString, not Python exact text/numeric hash | Explicit language adaptation |
| Collision resistance/security | No cryptographic guarantee added | Mathematical contract, not an equivalence/security proof |

## Corpus and property coverage

These are committed corpus counts, not final pass counts:

| Fixture kind | Records |
|---|---:|
| hash | 236 |
| power | 72 |
| phi | 468 |
| mod | 212 |
| mul | 288 |
| concatHash | 268 |
| repeatHash | 272 |
| overlapHash | 276 |
| rope metadata/bytes/height | 276 |
| split, both halves | 2,319 |
| substring | 5,148 |
| sliding complete state | 616 |
| expected errors | 93 |
| **Total** | **10,544** |

Main differential parameter sets are `(2^61-1,131)`, `(2^127-1,257)`, `(31,3)` and `(2^61-1,p-1)`, plus accepted positive non-Mersenne moduli. Seed: `5206534214300684357`. Oracle hashes, source hashes and exact record counts are pinned in [manifest.json](../java/src/test/resources/fixtures/manifest.json).

Property tests use `(2^61-1,131)`, `(2^127-1,257)`, `(31,3)` and `(127,17)` with base seed `0x48A5_2026_1022`:

| Property test | Coverage |
|---|---|
| `seededEditTraces` | 360 deterministic operations per profile: append/prepend, split/rejoin, repetition, replace ranges, substring queries, retained earlier roots; independent bytes/hash/metadata/balance checks after each step |
| `seededStreamingTraces` | 180 operations per profile: literal and overlapping/nonoverlapping copies; prefix/window/full hashes; eviction bounds; persistence of earlier window roots |
| `byteLengthsDoNotDetermineTreeWeights` | 140 alternating insertions per profile with one-byte and 1,024-byte leaves; exact weight and BB[2/7] checks |
| `arbitraryPrecisionRepeatsAndSplits` | Repetition count above 2^103; split/rejoin and substring near enormous logical end; independent modular repetition oracle; checked materialization rejection |
| `byteOwnershipAndSubstringQueriesPreserveExistingGraph` | Input and returned-array mutations, retained graph identities/metadata, many substring queries, raw UTF-8 continuation bytes |

## Historical 0.2.2 final verification

- Library clean install and separate consumer clean verify: **passed**, 10,608 + 2 JUnit tests, no failures/errors/skips; 3,868 arithmetic assertions execute within the library suite.
- Fresh golden fixture regeneration: byte-identical manifest and both TSV files; 10,544 oracle records.
- TDD replay: all 16 core, 23 standalone arithmetic, 25 sliding snapshots and both fixture-generator red/green pairs reproduced their expected outcomes. Four additional arithmetic Maven snapshots and the isolated extreme-modulus red/green watchdog have recorded successful replays.
- Main, sources and Javadoc JARs: built and inspected, Java 17 class version verified, exact source inclusion and ZIP integrity checked. [Artifact checksums and suite totals](../evidence/artifact-verification.json).
- Strict compiler/Javadoc checks and all five README examples: passed.
- Published Python reference tests: 146 passed; fixture-generator tests: 3 passed.
- Original source preservation: 748 files and six Git metadata files, original/nested HEADs, branches and dirty statuses unchanged. The detailed original inventory is local only; [aggregate evidence](../evidence/original-preservation-final.json) is public.
- GitHub destination: [jemsbhai/hashrope-java](https://github.com/jemsbhai/hashrope-java), public as requested. Final pushed commit identity is verified separately at delivery to avoid a self-referential commit hash.

See [VERIFICATION.md](VERIFICATION.md) for commands, evidence and deliberate unrun/excluded cases. Intentional language/termination boundaries above remain part of the contract even after all checks pass.

## 0.3.3 exact and profile additions

| Python / Rust feature | Java API | Verification |
|---|---|---|
| Exact content equality | Rope.equal | ExactComparisonTest collision, shape, profile, repeat, DAG and byte oracles |
| Exact byte LCP | Rope.lcp | First/last-byte mismatches and periodic witnesses |
| Exact checked ranges | Rope.rangeEqual / Rope.rangeLcp, BigInteger and long overloads | Nonzero starts, boundary rejection and arbitrary logical lengths |
| Immutable profile provenance | HashProfile, PolynomialHash.profile, Node.profile | Direct constructors, identity edit shortcuts, hash queries and streaming guards |

The historical arithmetic fixtures remain pinned to Python 0.2.2. Safety guards deliberately reject mixed profiles that older versions silently accepted; equal-profile subclass dispatch remains covered by the original tests. See [RELEASE-0.3.3.md](RELEASE-0.3.3.md) for those safety changes and [RELEASE-0.3.4.md](RELEASE-0.3.4.md) for the current parity-only release.

## 0.3.4 parity-only release

Java API behavior, polynomial fingerprints and comparison algorithms are unchanged from 0.3.3; only the reported package version changes. The sibling Rust package includes measured LCP/concat optimizations that have not been ported to Java. Rust evidence still includes an approximately 2% pinned LCP signal and mixed 100 KB comparison estimates; it establishes neither a universal speedup nor cross-language performance equivalence. See [the 0.3.4 release notes](RELEASE-0.3.4.md).
