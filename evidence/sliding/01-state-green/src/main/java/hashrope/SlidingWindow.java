package hashrope;

import java.math.BigInteger;

/**
 * A bounded-memory streaming polynomial hash with immutable rope snapshots.
 *
 * <p>The retained window may grow to {@code dMax + mMax} bytes. Appending past
 * that threshold evicts down to {@code dMax} bytes and folds the evicted data
 * into a prefix hash. Sizes and positions use arbitrary-precision integers.
 * Instances are mutable and are not thread-safe.</p>
 */
public final class SlidingWindow {
    private PolynomialHash h;
    private BigInteger dMax;
    private BigInteger mMax;
    private BigInteger W;
    private BigInteger hPrefix = BigInteger.ZERO;
    private BigInteger lPrefix = BigInteger.ZERO;
    private Node rWindow;
    private BigInteger pos = BigInteger.ZERO;

    /** Constructs a window with the Python defaults: distance 32768 and length 258. */
    public SlidingWindow() {
        this(32768, 258);
    }

    /**
     * Constructs a window using the default 61-bit Mersenne prime and base 131.
     * @param dMax retained bytes after eviction; must be positive
     * @param mMax additional capacity before eviction; must be positive
     * @throws IllegalArgumentException if either capacity parameter is not positive
     */
    public SlidingWindow(long dMax, long mMax) {
        this(BigInteger.valueOf(dMax), BigInteger.valueOf(mMax),
                PolynomialHash.MERSENNE_61, BigInteger.valueOf(131));
    }

    /**
     * Constructs a window with arbitrary-precision capacities and hash parameters.
     * @param dMax retained bytes after eviction; must be positive
     * @param mMax additional capacity before eviction; must be positive
     * @param prime polynomial hash modulus
     * @param base polynomial hash base
     * @throws IllegalArgumentException if a capacity is not positive or the base is invalid
     */
    public SlidingWindow(BigInteger dMax, BigInteger mMax, BigInteger prime, BigInteger base) {
        if (dMax.signum() < 1) {
            throw new IllegalArgumentException("d_max must be >= 1, got " + dMax);
        }
        if (mMax.signum() < 1) {
            throw new IllegalArgumentException("m_max must be >= 1, got " + mMax);
        }
        this.h = new PolynomialHash(prime, base);
        this.dMax = dMax;
        this.mMax = mMax;
        this.W = dMax.add(mMax);
    }

    /** Returns the hash calculator. @return hash parameters and operations */
    public PolynomialHash h() { return h; }
    /** Returns retained bytes after eviction. @return retention target */
    public BigInteger dMax() { return dMax; }
    /** Returns additional capacity before eviction. @return additional capacity */
    public BigInteger mMax() { return mMax; }
    /** Returns the eviction threshold. @return total window capacity */
    public BigInteger capacity() { return W; }
    /** Returns the hash of all evicted bytes. @return prefix hash */
    public BigInteger hPrefix() { return hPrefix; }
    /** Returns the number of evicted bytes. @return prefix length */
    public BigInteger lPrefix() { return lPrefix; }
    /** Returns the immutable retained rope snapshot. @return root, or null when empty */
    public Node window() { return rWindow; }
    /** Returns the decoded byte position. @return position */
    public BigInteger pos() { return pos; }
    /** Returns the retained byte length. @return window length */
    public BigInteger windowLen() { return Rope.len(rWindow); }

    /**
     * Composes the evicted prefix hash with the retained window hash.
     * @return hash of every byte appended so far
     */
    public BigInteger currentHash() {
        return h.hashConcat(hPrefix, windowLen(), Rope.hash(rWindow));
    }

    /**
     * Returns the current stream hash. This does not freeze the accumulator.
     * @return hash of every byte appended so far
     */
    public BigInteger finalHash() { return currentHash(); }
}
