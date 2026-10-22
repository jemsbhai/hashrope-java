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
}
