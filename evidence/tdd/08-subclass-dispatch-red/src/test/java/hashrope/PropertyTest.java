package hashrope;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Seeded properties use materialized byte arrays and plain modular arithmetic as oracles. */
class PropertyTest {
    private static final long SEED = 0x48A5_2026_1022L;
    private static final int MAX_BYTES = 2048;
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.TWO;

    private record Profile(String name, BigInteger prime, BigInteger base) {
        PolynomialHash hasher() { return new PolynomialHash(prime, base); }
    }
    private record Saved(Node root, byte[] bytes) { }
    private record Shape(BigInteger len, BigInteger weight, BigInteger hash, Node first, Node second) { }

    private static List<Profile> profiles() {
        return List.of(new Profile("mersenne61-base131", PolynomialHash.MERSENNE_61, n(131)),
            new Profile("mersenne127-base257", PolynomialHash.MERSENNE_127, n(257)),
            new Profile("mersenne5-base3", n(31), n(3)),
            new Profile("mersenne7-base17", n(127), n(17)));
    }

    @TestFactory Stream<DynamicTest> seededEditTraces() {
        return profiles().stream().map(p -> DynamicTest.dynamicTest(p.name + " edits seed=" + SEED, () -> editTrace(p)));
    }

    @TestFactory Stream<DynamicTest> seededStreamingTraces() {
        return profiles().stream().map(p -> DynamicTest.dynamicTest(p.name + " streaming seed=" + (SEED + 1), () -> streamTrace(p)));
    }

    @TestFactory Stream<DynamicTest> byteLengthsDoNotDetermineTreeWeights() {
        return profiles().stream().map(p -> DynamicTest.dynamicTest(p.name + " uneven leaf sizes", () -> {
            PolynomialHash h = p.hasher();
            Random random = new Random(SEED + 2);
            Node root = null;
            byte[] expected = new byte[0];
            for (int i = 0; i < 140; i++) {
                byte[] fragment = randomBytes(random, i % 13 == 0 ? 1024 : 1);
                Node leaf = new Leaf(fragment, h);
                if ((i & 1) == 0) {
                    root = Rope.concat(root, leaf, h);
                    expected = join(expected, fragment);
                } else {
                    root = Rope.concat(leaf, root, h);
                    expected = join(fragment, expected);
                }
                assertEquals(n(i + 1), root.weight(), "leaf weight at insertion " + i);
                assertInvariants(root, p);
            }
            assertArrayEquals(expected, Rope.toBytes(root));
            assertEquals(oracleHash(expected, p), Rope.hash(root));
            assertTrue(Rope.height(root) <= 4 * root.weight().bitLength() + 3);
        }));
    }

    @TestFactory Stream<DynamicTest> arbitraryPrecisionRepeatsAndSplits() {
        return profiles().stream().map(p -> DynamicTest.dynamicTest(p.name + " virtual bytes beyond 2^100", () -> {
            PolynomialHash h = p.hasher();
            byte[] pattern = {97, (byte)255, 0, 98, (byte)128};
            Node leaf = new Leaf(pattern, h);
            BigInteger q = ONE.shiftLeft(103).add(n(37));
            BigInteger total = q.multiply(n(pattern.length));
            Node repeated = Rope.repeat(leaf, q, h);
            assertEquals(total, repeated.len());
            assertEquals(q, repeated.weight());
            assertSame(leaf, ((RepeatNode) repeated).child());
            BigInteger expectedHash = oracleRepeatedHash(oracleHash(pattern, p), pattern.length, q, p);
            assertEquals(expectedHash, repeated.hashVal());
            BigInteger position = total.divide(TWO).add(n(2));
            Rope.Split split = Rope.split(repeated, position, h);
            assertEquals(position, Rope.len(split.left()));
            assertEquals(total.subtract(position), Rope.len(split.right()));
            assertInvariants(split.left(), p);
            assertInvariants(split.right(), p);
            assertEquals(expectedHash, combine(Rope.hash(split.left()), Rope.len(split.right()), Rope.hash(split.right()), p));
            Node rejoined = Rope.concat(split.left(), split.right(), h);
            assertEquals(total, Rope.len(rejoined));
            assertEquals(expectedHash, Rope.hash(rejoined));
            assertInvariants(rejoined, p);
            BigInteger start = total.subtract(n(211));
            byte[] sampled = new byte[173];
            int offset = start.mod(n(pattern.length)).intValueExact();
            for (int i = 0; i < sampled.length; i++) sampled[i] = pattern[(offset + i) % pattern.length];
            assertEquals(oracleHash(sampled, p), Rope.substrHash(repeated, start, n(sampled.length), h));
            assertSame(repeated, Rope.split(repeated, ZERO, h).right());
            assertSame(repeated, Rope.split(repeated, total, h).left());
            assertThrows(ArithmeticException.class, () -> Rope.toBytes(repeated));
        }));
    }

    @Test void byteOwnershipAndSubstringQueriesPreserveExistingGraph() {
        Profile p = profiles().get(0);
        PolynomialHash h = p.hasher();
        byte[] original = {(byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x80, 0, (byte)0xff};
        byte[] pristine = original.clone();
        Leaf leaf = new Leaf(original, h);
        Arrays.fill(original, (byte)7);
        Arrays.fill(leaf.data(), (byte)9);
        Node repeated = Rope.repeat(leaf, n(19), h);
        Node root = Rope.concat(new Leaf(new byte[]{1, 2, 3}, h), repeated, h);
        byte[] materialized = Rope.toBytes(root);
        Map<Node, Shape> before = new IdentityHashMap<>();
        snapshotGraph(root, before);
        for (int start = 0; start <= materialized.length; start++) {
            int length = Math.min(13, materialized.length - start);
            assertEquals(oracleHash(Arrays.copyOfRange(materialized, start, start + length), p),
                Rope.substrHash(root, n(start), n(length), h));
        }
        Map<Node, Shape> after = new IdentityHashMap<>();
        snapshotGraph(root, after);
        assertEquals(before.size(), after.size());
        before.forEach((node, shape) -> assertEquals(shape, after.get(node)));
        assertArrayEquals(pristine, leaf.data());
        assertArrayEquals(materialized, Rope.toBytes(root));
        // A byte index may cut through a UTF-8 code point; no text decoding is involved.
        assertEquals(oracleHash(new byte[]{(byte)0x9f, (byte)0x98}, p), Rope.substrHash(leaf, n(1), n(2), h));
    }

    private static void editTrace(Profile p) {
        PolynomialHash h = p.hasher();
        Random random = new Random(SEED);
        Node root = null;
        byte[] expected = new byte[0];
        List<Saved> saved = new ArrayList<>();
        for (int step = 0; step < 360; step++) {
            int operation = random.nextInt(6);
            if (operation == 0 || operation == 1) {
                byte[] fragment = randomBytes(random, random.nextInt(33));
                Node addition = Rope.fromBytes(fragment, h);
                if (operation == 0) { root = Rope.concat(root, addition, h); expected = join(expected, fragment); }
                else { root = Rope.concat(addition, root, h); expected = join(fragment, expected); }
            } else if (operation == 2) {
                int position = random.nextInt(expected.length + 17) - 8;
                int clamped = Math.max(0, Math.min(position, expected.length));
                Rope.Split split = Rope.split(root, n(position), h);
                assertArrayEquals(Arrays.copyOfRange(expected, 0, clamped), Rope.toBytes(split.left()));
                assertArrayEquals(Arrays.copyOfRange(expected, clamped, expected.length), Rope.toBytes(split.right()));
                assertInvariants(split.left(), p); assertInvariants(split.right(), p);
                root = Rope.concat(split.left(), split.right(), h);
            } else if (operation == 3) {
                int count = random.nextInt(4);
                if (expected.length * count <= MAX_BYTES) {
                    // Repeating the empty sentinel beyond once is outside Python's public constructor domain.
                    if (root != null || count <= 1) root = Rope.repeat(root, n(count), h);
                    expected = copies(expected, count);
                }
            } else if (operation == 4 && expected.length > 0) {
                int start = random.nextInt(expected.length + 1);
                int end = start + random.nextInt(expected.length - start + 1);
                Rope.Split left = Rope.split(root, n(start), h);
                Rope.Split right = Rope.split(left.right(), n(end - start), h);
                byte[] replacement = randomBytes(random, random.nextInt(20));
                root = Rope.concat(Rope.concat(left.left(), Rope.fromBytes(replacement, h), h), right.right(), h);
                expected = join(join(Arrays.copyOfRange(expected, 0, start), replacement), Arrays.copyOfRange(expected, end, expected.length));
            } else {
                int start = random.nextInt(expected.length + 1);
                int length = random.nextInt(expected.length - start + 1);
                assertEquals(oracleHash(Arrays.copyOfRange(expected, start, start + length), p), Rope.substrHash(root, n(start), n(length), h));
            }
            if (expected.length > MAX_BYTES) {
                int discard = expected.length - MAX_BYTES;
                root = Rope.split(root, n(discard), h).right();
                expected = Arrays.copyOfRange(expected, discard, expected.length);
            }
            assertEquals(n(expected.length), Rope.len(root), "length at step " + step);
            assertArrayEquals(expected, Rope.toBytes(root), "bytes at step " + step);
            assertEquals(oracleHash(expected, p), Rope.hash(root), "hash at step " + step);
            assertInvariants(root, p);
            Rope.validate(root, h);
            if (step % 59 == 0) saved.add(new Saved(root, expected.clone()));
            for (Saved old : saved) {
                assertArrayEquals(old.bytes, Rope.toBytes(old.root), "persistent snapshot at step " + step);
                assertEquals(oracleHash(old.bytes, p), Rope.hash(old.root));
            }
        }
    }

    private static void streamTrace(Profile p) {
        PolynomialHash h = p.hasher();
        SlidingWindow window = new SlidingWindow(n(31), n(17), p.prime, p.base);
        Random random = new Random(SEED + 1);
        byte[] expected = new byte[0];
        for (int step = 0; step < 180; step++) {
            Node savedWindow = window.window();
            byte[] savedBytes = Rope.toBytes(savedWindow);
            if (window.windowLen().signum() == 0 || random.nextBoolean()) {
                byte[] fragment = randomBytes(random, random.nextInt(24));
                window.appendBytes(fragment);
                expected = join(expected, fragment);
            } else {
                int offset = 1 + random.nextInt(window.windowLen().intValueExact());
                int length = random.nextInt(61);
                byte[] enlarged = Arrays.copyOf(expected, expected.length + length);
                for (int i = 0; i < length; i++) enlarged[expected.length + i] = enlarged[expected.length + i - offset];
                window.appendCopy(n(offset), n(length));
                expected = enlarged;
            }
            int prefix = window.lPrefix().intValueExact();
            assertEquals(n(expected.length), window.pos(), "position at streaming step " + step);
            assertEquals(oracleHash(expected, p), window.currentHash(), "stream hash at step " + step);
            assertEquals(window.currentHash(), window.finalHash());
            assertEquals(oracleHash(Arrays.copyOfRange(expected, 0, prefix), p), window.hPrefix());
            assertArrayEquals(Arrays.copyOfRange(expected, prefix, expected.length), Rope.toBytes(window.window()));
            assertEquals(n(expected.length - prefix), window.windowLen());
            assertTrue(window.windowLen().compareTo(n(48)) <= 0);
            assertArrayEquals(savedBytes, Rope.toBytes(savedWindow));
            assertInvariants(window.window(), p);
            Rope.validate(window.window(), h);
        }
    }

    /** Independent invariant check, including hashes that upstream validate_rope does not inspect. */
    private static void assertInvariants(Node node, Profile p) {
        if (node == null) return;
        if (node instanceof Leaf leaf) {
            assertEquals(ONE, leaf.weight());
            assertEquals(n(leaf.data().length), leaf.len());
            assertEquals(oracleHash(leaf.data(), p), leaf.hashVal());
        } else if (node instanceof Internal internal) {
            assertEquals(internal.left().len().add(internal.right().len()), internal.len());
            assertEquals(internal.left().weight().add(internal.right().weight()), internal.weight());
            BigInteger total = internal.weight();
            if (total.compareTo(TWO) > 0) {
                BigInteger leftSeven = internal.left().weight().multiply(n(7));
                assertTrue(leftSeven.compareTo(total.multiply(TWO)) >= 0, "left child too light");
                assertTrue(leftSeven.compareTo(total.multiply(n(5))) <= 0, "left child too heavy");
            }
            assertEquals(combine(internal.left().hashVal(), internal.right().len(), internal.right().hashVal(), p), internal.hashVal());
            assertInvariants(internal.left(), p); assertInvariants(internal.right(), p);
        } else if (node instanceof RepeatNode repeated) {
            assertTrue(repeated.reps().compareTo(TWO) >= 0);
            assertEquals(repeated.child().len().multiply(repeated.reps()), repeated.len());
            assertEquals(repeated.child().weight().multiply(repeated.reps()), repeated.weight());
            assertEquals(oracleRepeatedHash(repeated.child().hashVal(), repeated.child().len(), repeated.reps(), p), repeated.hashVal());
            assertInvariants(repeated.child(), p);
        } else fail("unknown sealed node type");
    }

    private static void snapshotGraph(Node node, Map<Node, Shape> output) {
        if (node == null || output.containsKey(node)) return;
        Node first = node instanceof Internal internal ? internal.left() : node instanceof RepeatNode repeat ? repeat.child() : null;
        Node second = node instanceof Internal internal ? internal.right() : null;
        output.put(node, new Shape(node.len(), node.weight(), node.hashVal(), first, second));
        snapshotGraph(first, output); snapshotGraph(second, output);
    }
    private static BigInteger oracleHash(byte[] bytes, Profile p) {
        BigInteger result = ZERO;
        for (byte value : bytes) result = result.multiply(p.base).add(n((value & 255) + 1)).mod(p.prime);
        return result;
    }
    private static BigInteger combine(BigInteger left, BigInteger rightLength, BigInteger right, Profile p) {
        return left.multiply(p.base.modPow(rightLength, p.prime)).add(right).mod(p.prime);
    }
    private static BigInteger oracleRepeatedHash(BigInteger hash, long length, BigInteger count, Profile p) {
        return oracleRepeatedHash(hash, n(length), count, p);
    }
    private static BigInteger oracleRepeatedHash(BigInteger hash, BigInteger length, BigInteger count, Profile p) {
        BigInteger accumulated = ZERO;
        BigInteger power = p.base.modPow(length, p.prime);
        BigInteger block = hash;
        for (BigInteger remaining = count; remaining.signum() > 0; remaining = remaining.shiftRight(1)) {
            if (remaining.testBit(0)) accumulated = accumulated.multiply(power).add(block).mod(p.prime);
            block = block.multiply(power).add(block).mod(p.prime);
            power = power.multiply(power).mod(p.prime);
        }
        return accumulated;
    }
    private static byte[] randomBytes(Random random, int length) {
        byte[] bytes = new byte[length]; random.nextBytes(bytes); return bytes;
    }
    private static byte[] join(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length); return result;
    }
    private static byte[] copies(byte[] source, int count) {
        byte[] result = new byte[source.length * count];
        for (int i = 0; i < count; i++) System.arraycopy(source, 0, result, i * source.length, source.length);
        return result;
    }
    private static BigInteger n(long value) { return BigInteger.valueOf(value); }
}
