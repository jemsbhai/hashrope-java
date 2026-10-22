package hashrope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Arrays;

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
        geometricAccumulator();
        concatenation();
        repeatedHashes();
        overlappingHashes();
        nonpositiveModuliAreRejected();
    }
    private static void nonpositiveModuliAreRejected() {
        fails(IllegalArgumentException.class,"Mersenne modulus must be positive, got 0",()->PolynomialHash.mersenneMod(BigInteger.ZERO,BigInteger.ZERO));
        fails(IllegalArgumentException.class,"Mersenne modulus must be positive, got -31",()->PolynomialHash.mersenneMod(BigInteger.ZERO,BigInteger.valueOf(-31)));
        fails(IllegalArgumentException.class,"Mersenne modulus must be positive, got 0",()->PolynomialHash.phi(BigInteger.TWO,BigInteger.ONE,BigInteger.ZERO));
        eq(BigInteger.ZERO,PolynomialHash.phi(BigInteger.ZERO,BigInteger.ONE,BigInteger.ZERO));
        eq(BigInteger.ONE,PolynomialHash.phi(BigInteger.ONE,BigInteger.ONE,BigInteger.ZERO));
        eq(BigInteger.valueOf(3),PolynomialHash.phi(BigInteger.valueOf(-5),BigInteger.TWO,BigInteger.valueOf(31)));
    }
    private static void overlappingHashes() {
        for(BigInteger p : new BigInteger[]{PolynomialHash.MERSENNE_61,PolynomialHash.MERSENNE_127}) {
            PolynomialHash h=new PolynomialHash(p,BigInteger.valueOf(131));
            byte[] pattern=new byte[]{0,(byte)255,1,65,64};
            for(int length=0;length<180;length++) {
                byte[] repeated=new byte[length];
                for(int i=0;i<length;i++) repeated[i]=pattern[i%pattern.length];
                BigInteger prefix=h.hash(Arrays.copyOf(pattern,length%pattern.length));
                eq(h.hash(repeated),h.hashOverlap(h.hash(pattern),pattern.length,length,prefix));
                eq(h.hash(repeated),h.hashOverlap(h.hash(pattern),BigInteger.valueOf(pattern.length),BigInteger.valueOf(length),prefix));
            }
        }
        PolynomialHash h=new PolynomialHash();
        fails(ArithmeticException.class,"integer division or modulo by zero",()->h.hashOverlap(BigInteger.ONE,0,3,BigInteger.ZERO));
        eq(new BigInteger("816090348086983744"),h.hashOverlap(BigInteger.valueOf(9),-3,10,BigInteger.valueOf(7)));
        eq(new BigInteger("30635930022662941"),h.hashOverlap(BigInteger.valueOf(9),3,-10,BigInteger.valueOf(7)));
        eq(new BigInteger("457929790439679507"),h.hashOverlap(BigInteger.valueOf(9),-3,-10,BigInteger.valueOf(7)));
        eq(BigInteger.valueOf(7),h.hashOverlap(BigInteger.valueOf(9),3,0,BigInteger.valueOf(7)));
    }
    private static void repeatedHashes() {
        for(BigInteger p : new BigInteger[]{PolynomialHash.MERSENNE_61,PolynomialHash.MERSENNE_127}) {
            PolynomialHash h=new PolynomialHash(p,BigInteger.valueOf(131));
            byte[] pattern=new byte[]{0,(byte)255,1,65};
            for(int q=0;q<35;q++) {
                byte[] repeated=new byte[pattern.length*q];
                for(int i=0;i<q;i++) System.arraycopy(pattern,0,repeated,i*pattern.length,pattern.length);
                eq(h.hash(repeated),h.hashRepeat(h.hash(pattern),pattern.length,q));
                eq(h.hash(repeated),h.hashRepeat(h.hash(pattern),BigInteger.valueOf(pattern.length),BigInteger.valueOf(q)));
            }
            BigInteger huge=BigInteger.ONE.shiftLeft(180).add(BigInteger.valueOf(19));
            BigInteger alpha=h.power(pattern.length);
            BigInteger sum=alpha.modPow(huge,p).subtract(BigInteger.ONE).multiply(alpha.subtract(BigInteger.ONE).modInverse(p)).mod(p);
            eq(h.hash(pattern).multiply(sum).mod(p),h.hashRepeat(h.hash(pattern),BigInteger.valueOf(pattern.length),huge));
            PolynomialHash degenerate=new PolynomialHash(p,p.subtract(BigInteger.ONE));
            byte[] two=new byte[]{(byte)255,1};
            eq(degenerate.hash(two).multiply(huge).mod(p),degenerate.hashRepeat(degenerate.hash(two),BigInteger.TWO,huge));
        }
    }
    private static void concatenation() {
        Random random = new Random(61127);
        for(BigInteger p : new BigInteger[]{PolynomialHash.MERSENNE_61,PolynomialHash.MERSENNE_127}) {
            PolynomialHash h=new PolynomialHash(p,BigInteger.valueOf(131));
            for(int i=0;i<80;i++) {
                byte[] left=new byte[random.nextInt(100)], right=new byte[random.nextInt(100)];
                random.nextBytes(left); random.nextBytes(right);
                byte[] joined=Arrays.copyOf(left,left.length+right.length);
                System.arraycopy(right,0,joined,left.length,right.length);
                eq(h.hash(joined),h.hashConcat(h.hash(left),right.length,h.hash(right)));
                eq(h.hash(joined),h.hashConcat(h.hash(left),BigInteger.valueOf(right.length),h.hash(right)));
            }
            BigInteger huge=BigInteger.ONE.shiftLeft(150);
            eq(BigInteger.valueOf(9).multiply(h.base().modPow(huge,p)).add(BigInteger.valueOf(7)).mod(p),h.hashConcat(BigInteger.valueOf(9),huge,BigInteger.valueOf(7)));
            eq(h.power(-7).multiply(BigInteger.valueOf(9)).add(BigInteger.valueOf(7)).mod(p),h.hashConcat(BigInteger.valueOf(9),-7,BigInteger.valueOf(7)));
        }
    }
    private static void geometricAccumulator() {
        for(BigInteger p : new BigInteger[]{BigInteger.valueOf(31),PolynomialHash.MERSENNE_61,PolynomialHash.MERSENNE_127}) {
            for(BigInteger alpha : new BigInteger[]{BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(3),p.subtract(BigInteger.ONE),p.add(BigInteger.ONE)}) {
                BigInteger expected=BigInteger.ZERO, power=BigInteger.ONE;
                for(int q=0;q<120;q++) {
                    eq(expected,PolynomialHash.phi(BigInteger.valueOf(q),alpha,p));
                    expected=expected.add(power).mod(p);
                    power=power.multiply(alpha).mod(p);
                }
            }
            BigInteger huge=BigInteger.ONE.shiftLeft(190).add(BigInteger.valueOf(12345));
            eq(huge.mod(p),PolynomialHash.phi(huge,BigInteger.ONE,p));
            eq(BigInteger.ONE,PolynomialHash.phi(huge,BigInteger.ZERO,p));
        }
        eq(BigInteger.valueOf(13),PolynomialHash.phi(BigInteger.valueOf(3),BigInteger.valueOf(3)));
        eq(BigInteger.ONE,PolynomialHash.phi(BigInteger.valueOf(-1),BigInteger.valueOf(3),BigInteger.valueOf(31)));
        eq(BigInteger.valueOf(4),PolynomialHash.phi(BigInteger.valueOf(-2),BigInteger.valueOf(3),BigInteger.valueOf(31)));
        eq(BigInteger.valueOf(13),PolynomialHash.phi(BigInteger.valueOf(-3),BigInteger.valueOf(3),BigInteger.valueOf(31)));
        eq(BigInteger.valueOf(28),PolynomialHash.phi(BigInteger.valueOf(-7),BigInteger.valueOf(3),BigInteger.valueOf(31)));
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
