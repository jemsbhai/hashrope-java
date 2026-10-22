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
