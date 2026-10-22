package hashrope;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SlidingWindowTest {
    private static BigInteger n(long value) { return BigInteger.valueOf(value); }

    @Test void defaultsAndEmptyStateMatchPython() {
        SlidingWindow window = new SlidingWindow();
        assertEquals(n(32768), window.dMax());
        assertEquals(n(258), window.mMax());
        assertEquals(n(33026), window.capacity());
        assertEquals(PolynomialHash.MERSENNE_61, window.h().prime());
        assertEquals(n(131), window.h().base());
        assertEquals(BigInteger.ZERO, window.pos());
        assertEquals(BigInteger.ZERO, window.hPrefix());
        assertEquals(BigInteger.ZERO, window.lPrefix());
        assertEquals(BigInteger.ZERO, window.windowLen());
        assertNull(window.window());
        assertEquals(BigInteger.ZERO, window.currentHash());
        assertEquals(window.currentHash(), window.finalHash());
    }

    @Test void constructorValidationKeepsPythonOrderAndMessages() {
        assertEquals("d_max must be >= 1, got 0",
                assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(0, 0)).getMessage());
        assertEquals("d_max must be >= 1, got -4",
                assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(-4, 1)).getMessage());
        assertEquals("m_max must be >= 1, got 0",
                assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(1, 0)).getMessage());
        assertEquals("m_max must be >= 1, got -9",
                assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(1, -9)).getMessage());
    }

    @Test void customProfilesAndArbitraryPrecisionCapacityArePreserved() {
        BigInteger huge = BigInteger.ONE.shiftLeft(130);
        SlidingWindow window = new SlidingWindow(huge, huge.add(n(3)), PolynomialHash.MERSENNE_127, n(257));
        assertEquals(huge.multiply(n(2)).add(n(3)), window.capacity());
        assertEquals(huge, window.dMax());
        assertEquals(huge.add(n(3)), window.mMax());
        assertEquals(PolynomialHash.MERSENNE_127, window.h().prime());
        assertEquals(n(257), window.h().base());
        assertEquals(BigInteger.ZERO, window.finalHash());
    }

    @Test void publicStateAssignmentsRemainObservableLikePythonSlots() {
        SlidingWindow window = new SlidingWindow();
        PolynomialHash replacement = new PolynomialHash(PolynomialHash.MERSENNE_127, n(257));
        window.h = replacement;
        window.dMax = n(9);
        window.mMax = n(11);
        window.W = n(12);
        window.hPrefix = replacement.hash(new byte[]{1, 2, 3});
        window.lPrefix = n(3);
        window.rWindow = new Leaf(new byte[]{4, 5, 6}, replacement);
        window.pos = n(6);
        assertSame(replacement, window.h());
        assertEquals(n(9), window.dMax());
        assertEquals(n(11), window.mMax());
        assertEquals(n(12), window.capacity());
        assertEquals(n(3), window.lPrefix());
        assertEquals(n(6), window.pos());
        assertSame(window.rWindow, window.window());
        assertEquals(replacement.hash(new byte[]{1, 2, 3, 4, 5, 6}), window.currentHash());
        window.rWindow = null;
        assertEquals(window.hPrefix, window.finalHash());
    }

    @Test void literalAppendsComposeBytesAndKeepImmutableSnapshots() {
        SlidingWindow window = new SlidingWindow(5, 5);
        byte[] first = new byte[]{0, (byte)128, (byte)255};
        window.appendBytes(first);
        Node snapshot = window.window();
        first[0] = 99;
        assertArrayEquals(new byte[]{0, (byte)128, (byte)255}, Rope.toBytes(snapshot));
        byte[] utf8 = "é💾".getBytes(StandardCharsets.UTF_8);
        window.appendBytes(utf8);
        byte[] expected = new byte[]{0, (byte)128, (byte)255,
                (byte)0xc3, (byte)0xa9, (byte)0xf0, (byte)0x9f, (byte)0x92, (byte)0xbe};
        assertArrayEquals(expected, Rope.toBytes(window.window()));
        assertEquals(n(9), window.pos());
        assertEquals(n(9), window.windowLen());
        assertEquals(BigInteger.ZERO, window.lPrefix());
        assertEquals(BigInteger.ZERO, window.hPrefix());
        assertEquals(window.h().hash(expected), window.currentHash());
        assertEquals(window.currentHash(), window.finalHash());
        assertArrayEquals(new byte[]{0, (byte)128, (byte)255}, Rope.toBytes(snapshot));
        byte[] exported = Rope.toBytes(window.window());
        exported[0] = 42;
        assertArrayEquals(expected, Rope.toBytes(window.window()));
        window.appendBytes(new byte[]{7});
        assertEquals(n(10), window.pos());
        assertEquals(n(10), window.windowLen());
        assertEquals(BigInteger.ZERO, window.lPrefix());
    }

    @Test void nullAndEmptyLiteralAppendsAreNoOps() {
        SlidingWindow window = new SlidingWindow();
        window.appendBytes(null);
        window.appendBytes(new byte[0]);
        assertNull(window.window());
        assertEquals(BigInteger.ZERO, window.pos());
        window.appendBytes(new byte[]{1});
        Node snapshot = window.window();
        window.appendBytes(null);
        window.appendBytes(new byte[0]);
        assertSame(snapshot, window.window());
        assertEquals(n(1), window.pos());
    }

    @Test void evictionOccursStrictlyAboveCapacityAndFoldsThePrefix() {
        SlidingWindow window = new SlidingWindow(3, 2);
        window.appendBytes("abcde".getBytes(StandardCharsets.US_ASCII));
        Node snapshot = window.window();
        assertEquals(n(5), window.windowLen());
        assertEquals(BigInteger.ZERO, window.lPrefix());
        window.appendBytes(new byte[]{'f'});
        assertArrayEquals("def".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(window.window()));
        assertEquals(n(6), window.pos());
        assertEquals(n(3), window.lPrefix());
        assertEquals(window.h().hash("abc".getBytes(StandardCharsets.US_ASCII)), window.hPrefix());
        assertEquals(window.h().hash("abcdef".getBytes(StandardCharsets.US_ASCII)), window.currentHash());
        assertArrayEquals("abcde".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(snapshot));
        window.appendBytes("ghijk".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals("ijk".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(window.window()));
        assertEquals(n(11), window.pos());
        assertEquals(n(8), window.lPrefix());
        assertEquals(window.h().hash("abcdefgh".getBytes(StandardCharsets.US_ASCII)), window.hPrefix());
        assertEquals(window.h().hash("abcdefghijk".getBytes(StandardCharsets.US_ASCII)), window.finalHash());
    }

    @Test void aLargeSingleAppendEvictsAndDirectCapacityAssignmentsTakeEffect() {
        SlidingWindow window = new SlidingWindow(4, 3);
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)i;
        window.appendBytes(bytes);
        assertArrayEquals(new byte[]{(byte)252, (byte)253, (byte)254, (byte)255}, Rope.toBytes(window.window()));
        assertEquals(n(252), window.lPrefix());
        assertEquals(n(256), window.pos());
        assertEquals(window.h().hash(bytes), window.currentHash());

        SlidingWindow assigned = new SlidingWindow(2, 2);
        assigned.W = n(6);
        assigned.appendBytes("ABCDEF".getBytes(StandardCharsets.US_ASCII));
        assertEquals(n(6), assigned.windowLen());
        assigned.dMax = n(3);
        assigned.mMax = n(100);
        assertEquals(n(6), assigned.capacity());
        assigned.appendBytes(new byte[]{'G'});
        assertArrayEquals("EFG".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(assigned.window()));
        assertEquals(n(4), assigned.lPrefix());
        assertEquals(assigned.h().hash("ABCDEFG".getBytes(StandardCharsets.US_ASCII)), assigned.currentHash());
    }

    @Test void nonoverlappingCopiesUseByteOffsetsAndPreserveEarlierRoots() {
        SlidingWindow window = new SlidingWindow(10, 10);
        window.appendBytes("abcdef".getBytes(StandardCharsets.US_ASCII));
        Node snapshot = window.window();
        window.appendCopy(4, 3);
        assertArrayEquals("abcdefcde".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(window.window()));
        assertEquals(n(9), window.pos());
        window.appendCopy(n(3), n(3));
        assertArrayEquals("abcdefcdecde".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(window.window()));
        assertEquals(n(12), window.pos());
        assertEquals(window.h().hash("abcdefcdecde".getBytes(StandardCharsets.US_ASCII)), window.currentHash());
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(snapshot));
        Node unchanged = window.window();
        window.appendCopy(0, 0);
        window.appendCopy(12, 0);
        assertSame(unchanged, window.window());
        assertEquals(n(12), window.pos());
    }

    @Test void copyLengthsAndDistancesAreNotArtificiallyCappedByCapacityParameters() {
        SlidingWindow longCopy = new SlidingWindow(6, 1);
        longCopy.appendBytes("abcdef".getBytes(StandardCharsets.US_ASCII));
        longCopy.appendCopy(6, 6);
        assertEquals(n(12), longCopy.pos());
        assertEquals(n(6), longCopy.lPrefix());
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(longCopy.window()));
        assertEquals(longCopy.h().hash("abcdefabcdef".getBytes(StandardCharsets.US_ASCII)), longCopy.currentHash());

        SlidingWindow distantCopy = new SlidingWindow(2, 10);
        distantCopy.appendBytes("abcdef".getBytes(StandardCharsets.US_ASCII));
        distantCopy.appendCopy(5, 4);
        assertEquals(n(10), distantCopy.pos());
        assertArrayEquals("abcdefbcde".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(distantCopy.window()));
        assertEquals(distantCopy.h().hash("abcdefbcde".getBytes(StandardCharsets.US_ASCII)), distantCopy.finalHash());
    }

    @Test void overlappingCopiesRepeatPatternsAndAppendPartialTails() {
        SlidingWindow window = new SlidingWindow(10, 20);
        window.appendBytes("abc".getBytes(StandardCharsets.US_ASCII));
        window.appendCopy(3, 8);
        assertArrayEquals("abcabcabcab".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(window.window()));
        window.appendCopy(1, 5);
        byte[] expected = "abcabcabcabbbbbb".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(expected, Rope.toBytes(window.window()));
        assertEquals(n(expected.length), window.pos());
        assertEquals(window.h().hash(expected), window.currentHash());

        SlidingWindow evicted = new SlidingWindow(3, 2);
        evicted.appendBytes("abc".getBytes(StandardCharsets.US_ASCII));
        evicted.appendCopy(2, 8);
        assertArrayEquals("cbc".getBytes(StandardCharsets.US_ASCII), Rope.toBytes(evicted.window()));
        assertEquals(n(11), evicted.pos());
        assertEquals(n(8), evicted.lPrefix());
        assertEquals(evicted.h().hash("abcbcbcbcbc".getBytes(StandardCharsets.US_ASCII)), evicted.currentHash());
    }

    @Test void enormousCopiesKeepArbitraryPrecisionPositionsWithoutMaterializing() {
        SlidingWindow window = new SlidingWindow(2, 3);
        byte[] seed = new byte[]{'a', 'b'};
        window.appendBytes(seed);
        Node original = window.window();
        BigInteger length = BigInteger.ONE.shiftLeft(70).add(BigInteger.ONE);
        window.appendCopy(n(2), length);
        assertEquals(length.add(n(2)), window.pos());
        assertEquals(length, window.lPrefix());
        assertEquals(n(2), window.windowLen());
        assertArrayEquals(new byte[]{'b', 'a'}, Rope.toBytes(window.window()));
        assertArrayEquals(seed, Rope.toBytes(original));
        BigInteger patternHash = window.h().hash(seed);
        BigInteger partialHash = window.h().hash(new byte[]{'a'});
        assertEquals(window.h().hashOverlap(patternHash, n(2), length, partialHash), window.hPrefix());
        assertEquals(window.h().hashOverlap(patternHash, n(2), length.add(n(2)), partialHash), window.currentHash());
    }

    @Test void invalidCopyBoundsKeepPythonMessagesOrderingAndState() {
        SlidingWindow window = new SlidingWindow(3, 2);
        window.appendBytes("abcdef".getBytes(StandardCharsets.US_ASCII));
        Node retained = window.window();
        BigInteger hash = window.currentHash();
        assertEquals("Invalid copy: offset 7 exceeds decoded length 6",
                assertThrows(IllegalArgumentException.class, () -> window.appendCopy(7, 0)).getMessage());
        assertEquals("Copy offset 4 exceeds window size 3. Increase d_max (currently 3) to at least 4.",
                assertThrows(IllegalArgumentException.class, () -> window.appendCopy(4, 1)).getMessage());
        assertThrows(ArithmeticException.class, () -> window.appendCopy(0, 1));
        assertSame(retained, window.window());
        assertEquals(n(6), window.pos());
        assertEquals(n(3), window.lPrefix());
        assertEquals(hash, window.currentHash());
        SlidingWindow empty = new SlidingWindow();
        assertEquals("Invalid copy: offset 1 exceeds decoded length 0",
                assertThrows(IllegalArgumentException.class, () -> empty.appendCopy(1, 1)).getMessage());
        assertThrows(ArithmeticException.class, () -> empty.appendCopy(0, 1));
        assertNull(empty.window());
        assertEquals(BigInteger.ZERO, empty.pos());
    }

    @Test void releaseNegativeInputQuirksArePreservedRatherThanSilentlyRejected() {
        long[][] cases = {{-1, 3, 6}, {-1, -2, 1}, {-2, -1, 2}, {2, -1, 2}, {2, -10, -7}};
        for (long[] test : cases) {
            SlidingWindow window = new SlidingWindow(3, 2);
            window.appendBytes("abc".getBytes(StandardCharsets.US_ASCII));
            Node root = window.window();
            BigInteger hash = window.currentHash();
            window.appendCopy(test[0], test[1]);
            assertSame(root, window.window());
            assertEquals(n(test[2]), window.pos());
            assertEquals(n(3), window.windowLen());
            assertEquals(hash, window.currentHash());
            if (test[2] < 0) {
                assertEquals("Invalid copy: offset 0 exceeds decoded length -7",
                        assertThrows(IllegalArgumentException.class, () -> window.appendCopy(0, 0)).getMessage());
            }
        }
    }
}
