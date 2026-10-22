package hashrope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/** Dependency-free executable arithmetic contract tests. */
public final class PolynomialHashTest {
    private static int assertions;
    private PolynomialHashTest() { }
    public static void main(String[] args) {
        runAll();
        System.out.println("PolynomialHashTest: " + assertions + " assertions passed");
    }
    public static void runAll() {
        defaultsAndBytes();
    }
    private static void defaultsAndBytes() {
        PolynomialHash h = new PolynomialHash();
        eq(BigInteger.ONE.shiftLeft(61).subtract(BigInteger.ONE), h.prime());
        eq(BigInteger.valueOf(131), h.base());
        eq(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE), PolynomialHash.MERSENNE_127);
        eq(BigInteger.ZERO, h.hash(new byte[0]));
        eq(BigInteger.ONE, h.hash(new byte[]{0}));
        eq(BigInteger.valueOf(256), h.hash(new byte[]{(byte)255}));
        eq(BigInteger.valueOf(132), h.hash(new byte[]{0,0}));
        eq(BigInteger.valueOf(1707554), h.hash("abc".getBytes(StandardCharsets.UTF_8)));
        PolynomialHash small = new PolynomialHash(31, 3);
        eq(BigInteger.valueOf(3), small.base());
        eq(BigInteger.valueOf(31), small.prime());
        eq(BigInteger.valueOf(13), small.hash(new byte[]{1,6}));
        fails(IllegalArgumentException.class, "Base must be in [2, p-1], got 1", () -> new PolynomialHash(31,1));
        fails(IllegalArgumentException.class, "Base must be in [2, p-1], got 31", () -> new PolynomialHash(31,31));
        fails(IllegalArgumentException.class, "Base must be in [2, p-1], got -9", () -> new PolynomialHash(31,-9));
    }
    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
    private static void fails(Class<? extends Throwable> type, String message, Runnable action) {
        assertions++;
        try { action.run(); } catch (Throwable e) {
            if (!type.isInstance(e) || !message.equals(e.getMessage())) throw new AssertionError("Unexpected exception",e);
            return;
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
