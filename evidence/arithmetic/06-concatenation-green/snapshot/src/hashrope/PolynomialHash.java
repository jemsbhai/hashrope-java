package hashrope;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Polynomial byte hashing with arbitrary-precision parameters and results. */
public final class PolynomialHash {
    /** The Mersenne prime {@code 2^61 - 1}. */
    public static final BigInteger MERSENNE_61 = BigInteger.ONE.shiftLeft(61).subtract(BigInteger.ONE);
    /** The Mersenne prime {@code 2^127 - 1}. */
    public static final BigInteger MERSENNE_127 = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
    private final BigInteger prime;
    private final BigInteger base;
    private final ConcurrentMap<BigInteger,BigInteger> powerCache = new ConcurrentHashMap<>();

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
        powerCache.put(BigInteger.ZERO,BigInteger.ONE);
        powerCache.put(BigInteger.ONE,base);
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
     * Computes and caches the base raised to an arbitrary-precision exponent.
     * Negative exponents use the modular inverse, matching Python's three-argument pow.
     * @param exponent signed exponent
     * @return the modular power
     * @throws IllegalArgumentException if a negative exponent needs an unavailable inverse
     */
    public BigInteger power(BigInteger exponent) {
        return powerCache.computeIfAbsent(exponent, n -> {
            try { return base.modPow(n,prime); }
            catch (ArithmeticException e) {
                throw new IllegalArgumentException("base is not invertible for the given modulus",e);
            }
        });
    }

    /**
     * Computes a power using a signed-long exponent.
     * @param exponent signed exponent
     * @return the modular power
     */
    public BigInteger power(long exponent) { return power(BigInteger.valueOf(exponent)); }

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
     * Computes {@code 1 + alpha + ... + alpha^(q-1)} by inverse-free doubling.
     * For nonnegative q this takes a number of arithmetic steps proportional to q's bit length.
     * As in Python 0.2.2, q=0 returns zero and q=1 returns one before arithmetic.
     * @param q repetition count; nonnegative for the geometric-sum contract
     * @param alpha geometric ratio
     * @param prime Mersenne modulus
     * @return the geometric accumulator
     */
    public static BigInteger phi(BigInteger q, BigInteger alpha, BigInteger prime) {
        if (q.signum() == 0) return BigInteger.ZERO;
        if (q.equals(BigInteger.ONE)) return BigInteger.ONE;
        BigInteger result = BigInteger.ONE;
        BigInteger alphaPower = alpha;
        for (int i=q.abs().bitLength()-2;i>=0;i--) {
            result = mersenneMul(result,BigInteger.ONE.add(alphaPower).mod(prime),prime);
            alphaPower = mersenneMul(alphaPower,alphaPower,prime);
            if (q.testBit(i)) {
                result = mersenneMul(result,alpha,prime).add(BigInteger.ONE).mod(prime);
                alphaPower = mersenneMul(alphaPower,alpha,prime);
            }
        }
        return result;
    }

    /**
     * Computes a geometric accumulator using {@link #MERSENNE_61}.
     * @param q repetition count
     * @param alpha geometric ratio
     * @return the geometric accumulator
     */
    public static BigInteger phi(BigInteger q, BigInteger alpha) { return phi(q,alpha,MERSENNE_61); }

    /**
     * Hashes bytes, treating each Java byte as an unsigned value in [0,255].
     * @param data byte sequence
     * @return its polynomial hash; zero for empty input
     */
    public BigInteger hash(byte[] data) {
        BigInteger result = BigInteger.ZERO;
        for (byte value : data) {
            result = mersenneMod(result.multiply(base).add(BigInteger.valueOf(Byte.toUnsignedInt(value) + 1L)),prime);
        }
        return result;
    }

    /**
     * Computes the hash of concatenated sequences from their hashes and the second length.
     * @param hashA first sequence's hash
     * @param lengthB second sequence's byte length
     * @param hashB second sequence's hash
     * @return {@code hashA * base^lengthB + hashB}, reduced by Mersenne folding
     */
    public BigInteger hashConcat(BigInteger hashA, BigInteger lengthB, BigInteger hashB) {
        return mersenneMod(hashA.multiply(power(lengthB)).add(hashB),prime);
    }

    /**
     * Concatenates hashes with a signed-long second length.
     * @param hashA first sequence's hash
     * @param lengthB second sequence's byte length
     * @param hashB second sequence's hash
     * @return the concatenated sequence's hash
     */
    public BigInteger hashConcat(BigInteger hashA, long lengthB, BigInteger hashB) {
        return hashConcat(hashA,BigInteger.valueOf(lengthB),hashB);
    }
}
