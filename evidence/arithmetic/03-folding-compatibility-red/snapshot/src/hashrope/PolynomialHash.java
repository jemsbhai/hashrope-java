package hashrope;

import java.math.BigInteger;

/** Polynomial byte hashing with arbitrary-precision parameters and results. */
public final class PolynomialHash {
    /** The Mersenne prime {@code 2^61 - 1}. */
    public static final BigInteger MERSENNE_61 = BigInteger.ONE.shiftLeft(61).subtract(BigInteger.ONE);
    /** The Mersenne prime {@code 2^127 - 1}. */
    public static final BigInteger MERSENNE_127 = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
    private final BigInteger prime;
    private final BigInteger base;

    /** Creates a hasher with the 61-bit Mersenne prime and base 131. */
    public PolynomialHash() { this(MERSENNE_61, BigInteger.valueOf(131)); }

    /**
     * Creates a hasher with explicit parameters.
     * @param prime Mersenne prime
     * @param base base between 2 and {@code prime - 1}, inclusive
     * @throws IllegalArgumentException if the base is outside that interval
     */
    public PolynomialHash(BigInteger prime, BigInteger base) {
        if (base.compareTo(BigInteger.TWO) < 0 || base.compareTo(prime) >= 0) {
            throw new IllegalArgumentException("Base must be in [2, p-1], got " + base);
        }
        this.prime = prime;
        this.base = base;
    }

    /**
     * Creates a hasher from signed-long parameter values.
     * @param prime Mersenne prime
     * @param base hash base
     */
    public PolynomialHash(long prime, long base) { this(BigInteger.valueOf(prime), BigInteger.valueOf(base)); }

    /** @return the configured modulus */
    public BigInteger prime() { return prime; }
    /** @return the configured base */
    public BigInteger base() { return base; }

    /**
     * Reduces a nonnegative arbitrary-precision integer with Mersenne folding.
     * The supplied modulus must have the form {@code 2^k - 1}.
     * @param value integer to reduce
     * @param prime Mersenne modulus
     * @return the reduced integer
     */
    public static BigInteger mersenneMod(BigInteger value, BigInteger prime) {
        int bits = prime.abs().bitLength();
        BigInteger threshold = BigInteger.ONE.shiftLeft(2 * bits);
        while (value.compareTo(threshold) >= 0) {
            value = value.shiftRight(bits).add(value.and(prime));
        }
        value = value.shiftRight(bits).add(value.and(prime));
        value = value.shiftRight(bits).add(value.and(prime));
        return value.compareTo(prime) >= 0 ? value.subtract(prime) : value;
    }

    /**
     * Reduces using {@link #MERSENNE_61}.
     * @param value integer to reduce
     * @return the reduced integer
     */
    public static BigInteger mersenneMod(BigInteger value) { return mersenneMod(value,MERSENNE_61); }

    /**
     * Multiplies without machine-integer overflow and reduces with Mersenne folding.
     * @param a first factor
     * @param b second factor
     * @param prime Mersenne modulus
     * @return the reduced product
     */
    public static BigInteger mersenneMul(BigInteger a, BigInteger b, BigInteger prime) {
        return mersenneMod(a.multiply(b),prime);
    }

    /**
     * Multiplies using {@link #MERSENNE_61}.
     * @param a first factor
     * @param b second factor
     * @return the reduced product
     */
    public static BigInteger mersenneMul(BigInteger a, BigInteger b) { return mersenneMul(a,b,MERSENNE_61); }

    /**
     * Hashes bytes, treating each Java byte as an unsigned value in [0,255].
     * @param data byte sequence
     * @return its polynomial hash; zero for empty input
     */
    public BigInteger hash(byte[] data) {
        BigInteger result = BigInteger.ZERO;
        for (byte value : data) {
            result = result.multiply(base).add(BigInteger.valueOf(Byte.toUnsignedInt(value) + 1L)).mod(prime);
        }
        return result;
    }
}
