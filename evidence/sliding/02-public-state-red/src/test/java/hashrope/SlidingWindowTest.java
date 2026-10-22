package hashrope;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
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
}
