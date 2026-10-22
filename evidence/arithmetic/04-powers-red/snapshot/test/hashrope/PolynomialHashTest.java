package hashrope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;

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
        reductionAndMultiplication();
        pythonFoldingCompatibility();
        powers();
    }
    private static void powers() {
        PolynomialHash h = new PolynomialHash();
        eq(BigInteger.ONE,h.power(0));
        eq(h.base(),h.power(1));
        eq(BigInteger.valueOf(17161),h.power(2));
        BigInteger huge = BigInteger.ONE.shiftLeft(137).add(BigInteger.valueOf(7));
        eq(h.base().modPow(huge,h.prime()),h.power(huge));
        eq(h.power(huge),h.power(huge));
        eq(h.base().modInverse(h.prime()),h.power(-1));
        eq(BigInteger.ONE,h.power(-21).multiply(h.power(21)).mod(h.prime()));
        eq(h.base().modInverse(h.prime()).modPow(huge,h.prime()),h.power(huge.negate()));
        fails(IllegalArgumentException.class,"base is not invertible for the given modulus",()->new PolynomialHash(15,3).power(-1));
    }
    private static void pythonFoldingCompatibility() {
        eq(BigInteger.ZERO,new PolynomialHash(17,3).hash(new byte[]{(byte)255,(byte)128,0}));
        eq(BigInteger.valueOf(30),PolynomialHash.mersenneMod(BigInteger.valueOf(-1),BigInteger.valueOf(31)));
        eq(BigInteger.ZERO,PolynomialHash.mersenneMod(BigInteger.valueOf(-31),BigInteger.valueOf(31)));
        eq(BigInteger.valueOf(-1),PolynomialHash.mersenneMod(BigInteger.valueOf(-1024),BigInteger.valueOf(31)));
        eq(new BigInteger("-1237940039285380274899124224"),PolynomialHash.mersenneMod(BigInteger.ONE.shiftLeft(100).negate(),BigInteger.valueOf(31)));
    }
    private static void reductionAndMultiplication() {
        Random random = new Random(0x48415348524f5045L);
        for (BigInteger p : new BigInteger[]{BigInteger.valueOf(3), BigInteger.valueOf(31), PolynomialHash.MERSENNE_61, PolynomialHash.MERSENNE_127}) {
            for (BigInteger a : new BigInteger[]{BigInteger.ZERO, BigInteger.ONE, p.subtract(BigInteger.ONE), p, p.add(BigInteger.ONE), p.multiply(p), BigInteger.ONE.shiftLeft(4096).add(BigInteger.valueOf(91))}) {
                eq(a.mod(p), PolynomialHash.mersenneMod(a,p));
            }
            for (int i=0;i<100;i++) {
                BigInteger a=new BigInteger(1+random.nextInt(2048),random);
                BigInteger b=new BigInteger(1+random.nextInt(2048),random);
                eq(a.mod(p),PolynomialHash.mersenneMod(a,p));
                eq(a.multiply(b).mod(p),PolynomialHash.mersenneMul(a,b,p));
            }
        }
        eq(BigInteger.ONE,PolynomialHash.mersenneMod(PolynomialHash.MERSENNE_61.add(BigInteger.ONE)));
        eq(BigInteger.valueOf(6),PolynomialHash.mersenneMul(BigInteger.TWO,BigInteger.valueOf(3)));
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
        eq(BigInteger.valueOf(1694847), h.hash("abc".getBytes(StandardCharsets.UTF_8)));
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
