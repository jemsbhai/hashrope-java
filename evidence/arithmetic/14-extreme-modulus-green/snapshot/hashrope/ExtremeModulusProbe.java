package hashrope;

import java.math.BigInteger;

/** Isolated resource-intensive regression; intentionally outside the default Maven suite. */
public final class ExtremeModulusProbe {
    private ExtremeModulusProbe() { }
    public static void main(String[] args) {
        BigInteger prime = BigInteger.ONE.shiftLeft(1_073_741_823);
        System.out.println("prime.bitLength=" + prime.bitLength());
        System.out.flush();
        BigInteger actual = PolynomialHash.mersenneMod(BigInteger.ZERO, prime);
        if (!BigInteger.ZERO.equals(actual)) throw new AssertionError("Expected zero, got " + actual);
        System.out.println("result=0");
    }
}
