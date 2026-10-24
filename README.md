# Hashrope for Java

A persistent byte rope with a polynomial hash at every node, compressed repetition and a streaming sliding window. Release 0.3.4 is a parity-only package release: Java runtime behavior and fingerprints are unchanged from 0.3.3, which added exact content equality/LCP and immutable hash-profile guards. Existing numeric behavior remains checked against the SHA256-pinned Python Hashrope 0.2.2 wheel.

- Java package: `hashrope`
- Maven coordinates: `io.github.jemsbhai:hashrope:0.3.4`
- Source/build directory: [`java/`](java/)
- Java baseline: compiled with `--release 17`; implementation build environment: JDK 24.0.1 and Maven 3.10.0
- No runtime dependencies; JUnit is used only for tests

Logical lengths, repetition counts, positions and hashes use `BigInteger`, including 61- and 127-bit Mersenne arithmetic. Immutable nodes share existing subtrees. All indexing is by **byte**, including when bytes encode UTF-8 text.

See the [compatibility contract](docs/COMPATIBILITY.md), [full API parity matrix](docs/PARITY-MATRIX.md) and [pinned fixture manifest](java/src/test/resources/fixtures/manifest.json). Polynomial hashes are not cryptographic digests; matching hashes alone do not prove matching data.

## Build and test

From this repository's root, with Java 17+ and Maven on PATH:

```text
mvn -s tools/isolated-settings.xml -gs tools/isolated-settings.xml -f java/pom.xml clean verify
```

The supplied empty user and global Maven settings avoid consulting saved publishing configuration. The build creates:

```text
java/target/hashrope-0.3.4.jar
java/target/hashrope-0.3.4-sources.jar
java/target/hashrope-0.3.4-javadoc.jar
```

Release coordinates (0.3.4 is published on Maven Central and [publicly verified](evidence/release-0.3.4/public-central/README.md)):

```xml
<dependency>
  <groupId>io.github.jemsbhai</groupId>
  <artifactId>hashrope</artifactId>
  <version>0.3.4</version>
</dependency>
```

**Release verification:** [0.3.4 parity-only release and validation](docs/RELEASE-0.3.4.md). [Published 0.3.3 validation and compatibility changes](docs/RELEASE-0.3.3.md) remain archived. Historical 0.2.2 publication and its original 10,608-test result remain documented in [the prior release report](docs/RELEASE.md).

## Exact equality and common prefixes

```java
import hashrope.*;
import java.math.BigInteger;

PolynomialHash h = new PolynomialHash();
Node a = Rope.fromBytes(new byte[] {0, (byte)131}, h);
Node b = Rope.fromBytes(new byte[] {1, 0}, h);
// These two fingerprints collide under the default profile.
boolean sameBytes = Rope.equal(a, b); // false
BigInteger prefixLength = Rope.lcp(a, b); // zero
boolean sameRange = Rope.rangeEqual(a, 0, b, 0, 2); // false
```

Exact methods compare actual bytes, skip shared immutable ranges, and prove repeated spans from finite periodic witnesses. They use a heap traversal stack and a per-call cache for shared graphs, without splitting, flattening or hashing. Independent equal inputs may require reading their full content; there is no general logarithmic bound. Range methods reject negative and out-of-bounds coordinates, even for empty ranges. Comparisons can cross fingerprint profiles; hash-bearing edits require matching profiles. `Node.equals` remains structural.


## Bytes, concatenation and splits

```java
import hashrope.Node;
import hashrope.PolynomialHash;
import hashrope.Rope;
import java.nio.charset.StandardCharsets;

PolynomialHash h = new PolynomialHash();
Node hello = Rope.fromBytes("hello ".getBytes(StandardCharsets.UTF_8), h);
Node world = Rope.fromBytes("world".getBytes(StandardCharsets.UTF_8), h);
Node joined = Rope.concat(hello, world, h);

if (!Rope.hash(joined).equals(h.hash("hello world".getBytes(StandardCharsets.UTF_8)))) {
    throw new AssertionError("hash mismatch");
}
Rope.Split halves = Rope.split(joined, 6, h);
byte[] prefix = Rope.toBytes(halves.left()); // hello followed by a space
byte[] suffix = Rope.toBytes(halves.right()); // world
Rope.validate(joined);
```

Empty input becomes a null rope; `Rope.len(null)` and `Rope.hash(null)` return zero. A split clamps positions outside the endpoints. Node fields are read through accessors such as `len()`, `weight()` and `hashVal()`. `Leaf.data()` returns a copy.

## Compressed repetition beyond long range

```java
import hashrope.Node;
import hashrope.PolynomialHash;
import hashrope.Rope;
import java.math.BigInteger;

PolynomialHash h = new PolynomialHash();
Node pattern = Rope.fromBytes(new byte[] {0, (byte) 255, 65}, h);
BigInteger copies = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7));
Node repeated = Rope.repeat(pattern, copies, h);

BigInteger totalBytes = Rope.len(repeated); // 3 * copies, exactly
Rope.Split halves = Rope.split(repeated, totalBytes.divide(BigInteger.TWO), h);
BigInteger sampleHash = Rope.substrHash(
    repeated, totalBytes.subtract(BigInteger.valueOf(20)), BigInteger.valueOf(17), h);
Rope.validate(halves.left());
Rope.validate(halves.right());
```

This does not expand the repetition into bytes. `Rope.toBytes(repeated)` would fail its checked Java array-size conversion; keep large ropes compressed and query their metadata or ranges.

## Streaming and overlapping copies

```java
import hashrope.PolynomialHash;
import hashrope.SlidingWindow;
import java.nio.charset.StandardCharsets;

SlidingWindow stream = new SlidingWindow(4, 3);
stream.appendBytes("abc".getBytes(StandardCharsets.UTF_8));
stream.appendCopy(3, 7); // append abcabca, including overlap

PolynomialHash h = new PolynomialHash();
if (!stream.currentHash().equals(h.hash("abcabcabca".getBytes(StandardCharsets.UTF_8)))) {
    throw new AssertionError("stream hash mismatch");
}
// Ten total bytes exceeded capacity seven; the retained window now has four bytes.
System.out.println(stream.pos());       // 10
System.out.println(stream.windowLen()); // 4
```

Eviction happens only above `dMax + mMax`, then retains exactly `dMax` bytes. These settings are not per-copy size limits. `finalHash()` returns the current hash without closing the stream. Public state fields preserve the Python API; changing them directly can invalidate stream invariants. SlidingWindow is not thread-safe.

## UTF-8 uses byte offsets

```java
import hashrope.Node;
import hashrope.PolynomialHash;
import hashrope.Rope;
import java.nio.charset.StandardCharsets;

PolynomialHash h = new PolynomialHash();
byte[] encoded = "A\u00e9\ud83d\ude00Z".getBytes(StandardCharsets.UTF_8);
Node text = Rope.fromBytes(encoded, h);
Rope.Split insideCharacter = Rope.split(text, 2, h);
// The first two bytes are 0x41, 0xc3: the split falls inside the UTF-8 encoding of é.
// Concatenating the halves restores the exact original bytes.
Node restored = Rope.concat(insideCharacter.left(), insideCharacter.right(), h);
if (!Rope.hash(restored).equals(h.hash(encoded))) throw new AssertionError();
```

Use byte-aligned ranges appropriate to your application. The port preserves Python's historical behavior for malformed substring ranges and negative copy dimensions, even though those inputs can produce representation-dependent hashes or inconsistent streaming counters. The [compatibility contract](docs/COMPATIBILITY.md) explains these cases and the intentional nonpositive-modulus rejection.

## Choose arithmetic parameters

```java
import hashrope.PolynomialHash;
import java.math.BigInteger;

PolynomialHash h127 = new PolynomialHash(
    PolynomialHash.MERSENNE_127, BigInteger.valueOf(257));
```

Use one consistent prime/base context for hash-bearing edits. `HashProfile`, `PolynomialHash.profile()` and `Node.profile()` expose immutable provenance; incompatible composition and stream profile replacement are rejected. The constructor checks the base interval but does not validate primality or Mersenne form, matching the Python release. Positive non-Mersenne moduli retain the reference's folding behavior, which need not equal ordinary modulo arithmetic.

## Reproduce the Python fixtures

Download the pinned wheel referenced in [manifest.json](java/src/test/resources/fixtures/manifest.json), then run outside the original source repository:

```text
python -B tools/generate_fixtures.py --wheel /path/to/hashrope-0.2.2-py3-none-any.whl --output java/src/test/resources/fixtures
```

The generator verifies the wheel hash before import, disables bytecode output and uses a fixed seed. Fixtures store arbitrary integers as decimal text and bytes as hex. See [the schema](java/src/test/resources/fixtures/SCHEMA.md) for record details. Differential tests compare hashes, lengths, weights, heights, bytes, splits, streams and mapped errors; independent property tests check composition and persistence against byte-array models.

The port lives in its own `hashrope-java` repository and `java/` directory. The original Python repository is preserved read-only. Source distribution licensing is MIT; see [LICENSE](LICENSE).
