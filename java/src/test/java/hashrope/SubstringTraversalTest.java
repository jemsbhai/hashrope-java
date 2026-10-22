package hashrope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests observable hasher overrides and metadata-based substring traversal. */
class SubstringTraversalTest {
    private static final int LEAF_BYTES = 64;
    private static final int LEAF_COUNT = 1024;

    private static final class CountingHash extends PolynomialHash {
        int hashCalls;
        long bytesHashed;
        int visitedRanges;

        @Override public BigInteger hash(byte[] data) {
            hashCalls++;
            bytesHashed += data.length;
            return super.hash(data);
        }

        @Override public BigInteger prime() {
            visitedRanges++;
            return super.prime();
        }
    }

    @Test void wholeRootUsesStoredHashWithoutRehashingOrDescending() {
        PolynomialHash normal = new PolynomialHash();
        Node root = balancedTree(bytes(), normal, 0, LEAF_COUNT);
        CountingHash spy = new CountingHash();

        assertEquals(root.hashVal(), Rope.substrHash(root, BigInteger.ZERO, root.len(), spy));
        assertEquals(0, spy.hashCalls);
        assertEquals(0, spy.bytesHashed);
        assertEquals(1, spy.visitedRanges);
    }

    @Test void spanningRangeRehashesOnlyTwoPartialBoundaryLeaves() {
        PolynomialHash normal = new PolynomialHash();
        byte[] data = bytes();
        Node root = balancedTree(data, normal, 0, LEAF_COUNT);
        CountingHash spy = new CountingHash();
        int start = 13;
        int end = data.length - 17;

        assertEquals(normal.hash(Arrays.copyOfRange(data, start, end)),
                Rope.substrHash(root, start, end - start, spy));
        assertEquals(2, spy.hashCalls);
        assertEquals((LEAF_BYTES - 13) + (LEAF_BYTES - 17), spy.bytesHashed);
        assertTrue(spy.visitedRanges <= 4 * Rope.height(root) + 2,
                "range traversal should visit only boundary paths and covered subtrees");
    }

    @Test void largePrefixReusesCompleteSubtreeHashes() {
        PolynomialHash normal = new PolynomialHash();
        byte[] data = bytes();
        Node root = balancedTree(data, normal, 0, LEAF_COUNT);
        CountingHash spy = new CountingHash();
        int end = data.length - 17;

        assertEquals(normal.hash(Arrays.copyOf(data, end)), Rope.substrHash(root, 0, end, spy));
        assertEquals(1, spy.hashCalls);
        assertEquals(LEAF_BYTES - 17, spy.bytesHashed);
        assertTrue(spy.visitedRanges <= 2 * Rope.height(root) + 1,
                "fully covered prefix subtrees must not be traversed leaf by leaf");
    }

    @Test void hugeRepeatRangeHashesOnlyBoundariesWithoutMaterialization() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            PolynomialHash normal = new PolynomialHash();
            byte[] period = "abcdefg".getBytes(StandardCharsets.US_ASCII);
            BigInteger seven = BigInteger.valueOf(7);
            BigInteger repetitions = BigInteger.ONE.shiftLeft(160).add(BigInteger.valueOf(9));
            Node root = Rope.repeat(new Leaf(period, normal), repetitions, normal);
            BigInteger start = BigInteger.ONE.shiftLeft(130).multiply(seven).add(BigInteger.TWO);
            BigInteger fullRotatedPeriods = BigInteger.ONE.shiftLeft(120);
            BigInteger length = fullRotatedPeriods.multiply(seven).add(BigInteger.valueOf(4));
            BigInteger expected = normal.hashConcat(
                    normal.hashRepeat(normal.hash("cdefgab".getBytes(StandardCharsets.US_ASCII)),
                            seven, fullRotatedPeriods),
                    4, normal.hash("cdef".getBytes(StandardCharsets.US_ASCII)));
            CountingHash spy = new CountingHash();

            assertEquals(expected, Rope.substrHash(root, start, length, spy));
            assertEquals(2, spy.hashCalls);
            assertEquals(11, spy.bytesHashed);
            assertEquals(3, spy.visitedRanges);
            assertTrue(length.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        });
    }

    private static byte[] bytes() {
        byte[] data = new byte[LEAF_COUNT * LEAF_BYTES];
        for (int i = 0; i < data.length; i++) data[i] = (byte)(i * 137 + 19);
        return data;
    }

    private static Node balancedTree(byte[] data, PolynomialHash h, int firstLeaf, int leaves) {
        if (leaves == 1) {
            int from = firstLeaf * LEAF_BYTES;
            return new Leaf(Arrays.copyOfRange(data, from, from + LEAF_BYTES), h);
        }
        int half = leaves / 2;
        return new Internal(balancedTree(data, h, firstLeaf, half),
                balancedTree(data, h, firstLeaf + half, leaves - half), h);
    }
}
