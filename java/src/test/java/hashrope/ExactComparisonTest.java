package hashrope;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactComparisonTest {
    private static final PolynomialHash H = new PolynomialHash();
    private static BigInteger n(long value) { return BigInteger.valueOf(value); }
    private static Node bytes(byte... value) { return Rope.fromBytes(value, H); }
    private static Node chunked(byte[] value, int width, PolynomialHash h) {
        Node result = null;
        for (int i = 0; i < value.length; i += width) {
            result = Rope.concat(result, Rope.fromBytes(Arrays.copyOfRange(value, i, Math.min(i + width, value.length)), h), h);
        }
        return result;
    }
    private static int common(byte[] a, int x, byte[] b, int y, int length) {
        int i = 0;
        while (i < length && a[x + i] == b[y + i]) i++;
        return i;
    }

    @Test void collisionsAreNotEqualityOrPrefixEvidence() {
        Node a = bytes((byte)0, (byte)131), b = bytes((byte)1, (byte)0);
        assertEquals(Rope.hash(a), Rope.hash(b));
        assertFalse(Rope.equal(a, b));
        assertEquals(n(0), Rope.lcp(a, b));
        Node prefix = bytes((byte)9, (byte)8);
        a = Rope.concat(prefix, a, H); b = Rope.concat(prefix, b, H);
        assertEquals(n(2), Rope.lcp(a, b));
        assertFalse(Rope.equal(a, b));
    }

    @Test void allLeafMismatchPositionsAndRangesUseByteIndices() {
        for (int length : new int[]{1, 7, 8, 9, 63, 64, 65, 255, 512}) {
            byte[] a = new byte[length + 9];
            new Random(length).nextBytes(a);
            for (int at = 0; at < length; at++) {
                byte[] b = a.clone(); b[at + 3] ^= (byte)(1 << (at % 8));
                Node x = Rope.fromBytes(a, H), y = Rope.fromBytes(b, H);
                assertEquals(n(at), Rope.rangeLcp(x, 3, y, 3, length));
                assertFalse(Rope.rangeEqual(x, 3, y, 3, length));
            }
        }
    }

    @Test void independentShapesProfilesAndRandomClippedRangesMatchPlainBytes() {
        Random random = new Random(0x6ad2b13L);
        PolynomialHash other = new PolynomialHash(PolynomialHash.MERSENNE_127, n(257));
        for (int round = 0; round < 80; round++) {
            byte[] a = new byte[1 + random.nextInt(1200)]; random.nextBytes(a);
            byte[] b = a.clone();
            if (round % 3 != 0) b[random.nextInt(b.length)] ^= 1;
            Node x = chunked(a, 1 + random.nextInt(64), H);
            Node y = chunked(b, 1 + random.nextInt(79), other);
            assertEquals(Arrays.equals(a, b), Rope.equal(x, y));
            assertEquals(n(common(a, 0, b, 0, a.length)), Rope.lcp(x, y));
            for (int probe = 0; probe < 12; probe++) {
                int startA = random.nextInt(a.length + 1), startB = random.nextInt(b.length + 1);
                int length = random.nextInt(Math.min(a.length - startA, b.length - startB) + 1);
                int expected = common(a, startA, b, startB, length);
                assertEquals(n(expected), Rope.rangeLcp(x, startA, y, startB, length));
                assertEquals(expected == length, Rope.rangeEqual(x, startA, y, startB, length));
            }
        }
    }

    @Test void arbitraryPrecisionRepeatsDifferingPeriodsAndShiftedInternalBoundariesStayCompressed() {
        BigInteger count = BigInteger.ONE.shiftLeft(160).add(n(2));
        Node left = Rope.repeat(bytes((byte)'a', (byte)'b'), count, H);
        Node right = Rope.repeat(bytes((byte)'a', (byte)'b', (byte)'a', (byte)'b'), count.divide(n(2)), H);
        assertTrue(Rope.equal(left, right));
        assertEquals(count.multiply(n(2)), Rope.lcp(left, right));
        Node middle = Rope.repeat(bytes((byte)'b', (byte)'a'), count.subtract(BigInteger.ONE), H);
        Node shifted = Rope.concat(Rope.concat(bytes((byte)'a'), middle, H), bytes((byte)'b'), H);
        assertTrue(Rope.equal(left, shifted));
        assertEquals(count.multiply(n(2)).subtract(n(7)), Rope.rangeLcp(left, n(3), shifted, n(3), count.multiply(n(2)).subtract(n(7))));
        Node p = Rope.repeat(bytes((byte)'a', (byte)'a', (byte)'b'), count, H);
        Node q = Rope.repeat(bytes((byte)'a', (byte)'a', (byte)'b', (byte)'a'), count, H);
        assertEquals(n(5), Rope.lcp(p, q));
    }

    @Test void independentlyAllocatedSelfConcatDagsAndLastByteEditsStayCompressed() {
        Node a = bytes((byte)'a', (byte)'b'), b = bytes((byte)'a', (byte)'b');
        for (int i = 0; i < 180; i++) { a = new Internal(a, a, H); b = new Internal(b, b, H); }
        assertTrue(Rope.equal(a, b));
        BigInteger end = a.len().subtract(BigInteger.ONE);
        Rope.Split split = Rope.split(b, end, H);
        Node changed = Rope.concat(split.left(), bytes((byte)'X'), H);
        assertEquals(end, Rope.lcp(a, changed));
        assertFalse(Rope.equal(a, changed));
    }

    @Test void explicitTaskStackDoesNotUseRecursiveNodeEqualsOrHashCode() {
        Node unitA = bytes((byte)'a'), unitB = bytes((byte)'a');
        Node a = unitA, b = unitB;
        for (int i = 0; i < 5000; i++) { a = new Internal(a, unitA, H); b = new Internal(b, unitB, H); }
        assertTrue(Rope.equal(a, b));
        assertEquals(n(5001), Rope.lcp(a, b));
        assertTrue(Rope.rangeEqual(a, 17, b, 17, 4000));
    }

    @Test void exactRangeBoundsAreCheckedEvenForIdentityAndEmptyInputs() {
        Node a = bytes((byte)1, (byte)2);
        assertTrue(Rope.equal(null, null));
        assertEquals(BigInteger.ZERO, Rope.lcp(a, null));
        assertTrue(Rope.rangeEqual(null, 0, null, 0, 0));
        assertTrue(Rope.rangeEqual(a, 2, a, 2, 0));
        for (long[] range : new long[][]{{-1, 0}, {0, -1}, {3, 0}, {1, 2}}) {
            assertThrows(IllegalArgumentException.class, () -> Rope.rangeEqual(a, range[0], a, range[0], range[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> Rope.rangeLcp(null, 1, null, 1, 0));
    }

    @Test void mixedProfilesAreRejectedEvenOnHashOrEditIdentityPaths() {
        PolynomialHash other = new PolynomialHash(PolynomialHash.MERSENNE_61, n(257));
        Node a = bytes((byte)'a', (byte)'b'), b = Rope.fromBytes(new byte[]{'a', 'b'}, other);
        assertTrue(Rope.equal(a, b));
        assertEquals(n(2), Rope.lcp(a, b));
        assertThrows(IllegalArgumentException.class, () -> new Internal(a, b, H));
        assertThrows(IllegalArgumentException.class, () -> new RepeatNode(b, 2, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.concat(null, b, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.repeat(b, 0, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.repeat(b, 1, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.split(b, 0, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.substrHash(b, 0, 0, H));
        assertThrows(IllegalArgumentException.class, () -> Rope.validate(b, H));
        assertDoesNotThrow(() -> Rope.validate(b));
        assertSame(a, Rope.concat(a, null, new PolynomialHash()));
    }

    @Test void immutableProfilesDoNotChangeStructuralEqualityOrCustomFingerprintSemantics() {
        PolynomialHash custom = new PolynomialHash(17, 2);
        Node flat = Rope.fromBytes(new byte[]{0, 13}, custom);
        Node joined = Rope.concat(Rope.fromBytes(new byte[]{0}, custom), Rope.fromBytes(new byte[]{13}, custom), custom);
        assertNotEquals(Rope.hash(flat), Rope.hash(joined));
        assertTrue(Rope.equal(flat, joined));
        assertEquals(n(2), Rope.lcp(flat, joined));
        assertEquals(new HashProfile(PolynomialHash.MERSENNE_61, n(131)), H.profile());
        Node singleA = bytes((byte)1), singleB = Rope.fromBytes(new byte[]{1}, new PolynomialHash(PolynomialHash.MERSENNE_61, n(257)));
        assertEquals(singleA, singleB); // Historical structural equality excludes provenance.
    }

    @Test void slidingProfileReplacementCannotMixAnExistingPrefixOrWindow() {
        SlidingWindow stream = new SlidingWindow(4, 3);
        stream.appendBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Node old = stream.window(); BigInteger pos = stream.pos();
        stream.h = new PolynomialHash(PolynomialHash.MERSENNE_61, n(257));
        assertThrows(IllegalArgumentException.class, stream::currentHash);
        assertThrows(IllegalArgumentException.class, () -> stream.appendBytes(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> stream.appendCopy(1, 0));
        assertThrows(IllegalArgumentException.class, () -> stream.appendBytes(new byte[]{9}));
        assertSame(old, stream.window()); assertEquals(pos, stream.pos());
        stream.h = new PolynomialHash();
        assertEquals(H.hash(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}), stream.currentHash());
        stream.appendCopy(1, 2);
        assertEquals(H.hash(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 8}), stream.currentHash());
    }
}
