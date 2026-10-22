package hashrope;

import java.math.BigInteger;

/**
 * A streaming polynomial hash with a bounded byte window and immutable rope snapshots.
 *
 * <p>The retained window may grow to {@code dMax + mMax} bytes. Appending past
 * that threshold evicts down to {@code dMax} bytes and folds the evicted data
 * into a prefix hash. Sizes and positions use arbitrary-precision integers.
 * The threshold bounds represented retained bytes for valid operations, not
 * total JVM allocation or the hash calculator's unbounded power cache.
 * Instances are mutable and are not thread-safe. Public state fields preserve
 * Python's assignable slots; direct assignments can violate stream invariants.</p>
 */
public class SlidingWindow {
    /** Hash calculator; corresponds to Python's {@code h} slot. */
    public PolynomialHash h;
    /** Retained bytes after eviction; corresponds to {@code d_max}. */
    public BigInteger dMax;
    /** Extra capacity; corresponds to {@code m_max}. */
    public BigInteger mMax;
    /** Eviction threshold, initialized to {@code dMax + mMax}. */
    public BigInteger W;
    /** Hash of evicted bytes; corresponds to {@code h_prefix}. */
    public BigInteger hPrefix = BigInteger.ZERO;
    /** Number of evicted bytes; corresponds to {@code l_prefix}. */
    public BigInteger lPrefix = BigInteger.ZERO;
    /** Retained rope root, or null; corresponds to {@code r_window}. */
    public Node rWindow;
    /** Total decoded position; corresponds to Python's {@code pos} slot. */
    public BigInteger pos = BigInteger.ZERO;

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
     * Constructs a default-profile window with arbitrary-precision capacities.
     * @param dMax retained bytes after eviction; must be positive
     * @param mMax additional capacity before eviction; must be positive
     * @throws IllegalArgumentException if either capacity is not positive
     */
    public SlidingWindow(BigInteger dMax, BigInteger mMax) {
        this(dMax, mMax, PolynomialHash.MERSENNE_61, BigInteger.valueOf(131));
    }

    /**
     * Constructs a custom-profile window with convenient long capacities.
     * @param dMax retained bytes after eviction; must be positive
     * @param mMax additional capacity before eviction; must be positive
     * @param prime polynomial hash modulus
     * @param base polynomial hash base
     * @throws IllegalArgumentException if a capacity is not positive or the base is invalid
     */
    public SlidingWindow(long dMax, long mMax, BigInteger prime, BigInteger base) {
        this(BigInteger.valueOf(dMax), BigInteger.valueOf(mMax), prime, base);
    }

    /**
     * Constructs a custom-profile window using long convenience parameters.
     * @param dMax retained bytes after eviction; must be positive
     * @param mMax additional capacity before eviction; must be positive
     * @param prime polynomial hash modulus
     * @param base polynomial hash base
     * @throws IllegalArgumentException if a capacity is not positive or the base is invalid
     */
    public SlidingWindow(long dMax, long mMax, long prime, long base) {
        this(dMax, mMax, BigInteger.valueOf(prime), BigInteger.valueOf(base));
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

    /**
     * Returns the hash calculator.
     * @return hash parameters and operations
     */
    public PolynomialHash h() { return h; }
    /**
     * Returns retained bytes after eviction.
     * @return retention target
     */
    public BigInteger dMax() { return dMax; }
    /**
     * Returns additional capacity before eviction.
     * @return additional capacity
     */
    public BigInteger mMax() { return mMax; }
    /**
     * Returns the eviction threshold.
     * @return total window capacity
     */
    public BigInteger capacity() { return W; }
    /**
     * Returns the hash of all evicted bytes.
     * @return prefix hash
     */
    public BigInteger hPrefix() { return hPrefix; }
    /**
     * Returns the number of evicted bytes.
     * @return prefix length
     */
    public BigInteger lPrefix() { return lPrefix; }
    /**
     * Returns the immutable retained rope snapshot.
     * @return root, or null when empty
     */
    public Node window() { return rWindow; }
    /**
     * Returns the decoded byte position.
     * @return position
     */
    public BigInteger pos() { return pos; }
    /**
     * Returns the retained byte length.
     * @return window length
     */
    public BigInteger windowLen() { return Rope.len(rWindow); }

    /**
     * Composes the evicted prefix hash with the retained window hash.
     * @return hash of every byte appended so far
     */
    public BigInteger currentHash() {
        BigInteger windowLength = windowLen();
        BigInteger windowHash = Rope.hash(rWindow);
        return h.hashConcat(hPrefix, windowLength, windowHash);
    }

    /**
     * Returns the current stream hash. This does not freeze the accumulator.
     * @return hash of every byte appended so far
     */
    public BigInteger finalHash() { return currentHash(); }

    /**
     * Appends literal bytes. Null and empty arrays are no-ops, as in Python.
     * The byte contents are copied, so later caller mutations are harmless.
     * @param data literal bytes, or null
     */
    public void appendBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        rWindow = Rope.concat(rWindow, new Leaf(data, h), h);
        pos = pos.add(BigInteger.valueOf(data.length));
        evict();
    }

    /**
     * Appends a copy from the retained window using arbitrary-precision sizes.
     * Overlapping copies repeat a retained pattern without materializing it.
     * Copy lengths may exceed {@link #mMax}; distances may exceed {@link #dMax}
     * if the source is still retained. Negative inputs preserve the Python
     * release's incidental behavior and may violate stream invariants.
     * @param offset byte distance backwards from the end of the window
     * @param length number of bytes to append
     * @throws IllegalArgumentException if offset exceeds decoded position or retained length
     * @throws ArithmeticException if offset is zero and length is positive
     */
    public void appendCopy(BigInteger offset, BigInteger length) {
        BigInteger windowLength = windowLen();
        if (offset.compareTo(pos) > 0) {
            throw new IllegalArgumentException("Invalid copy: offset " + offset
                    + " exceeds decoded length " + pos);
        }
        if (offset.compareTo(windowLength) > 0) {
            throw new IllegalArgumentException("Copy offset " + offset
                    + " exceeds window size " + windowLength + ". Increase d_max (currently "
                    + dMax + ") to at least " + offset + ".");
        }
        BigInteger start = windowLength.subtract(offset);
        Node temporary = Rope.split(rWindow, start, h).right();
        Node source;
        if (offset.compareTo(length) >= 0) {
            source = Rope.split(temporary, length, h).left();
        } else {
            Node pattern = Rope.split(temporary, offset, h).left();
            BigInteger[] division = floorDivideAndRemainder(length, offset);
            source = division[0].signum() > 0 ? Rope.repeat(pattern, division[0], h) : null;
            if (division[1].signum() > 0) {
                Node partial = Rope.split(pattern, division[1], h).left();
                source = Rope.concat(source, partial, h);
            }
        }
        rWindow = Rope.concat(rWindow, source, h);
        pos = pos.add(length);
        evict();
    }

    /**
     * Appends a copy using {@code long} convenience parameters.
     * @param offset byte distance backwards from the end of the window
     * @param length number of bytes to append
     * @throws IllegalArgumentException if offset exceeds decoded position or retained length
     * @throws ArithmeticException if offset is zero and length is positive
     */
    public void appendCopy(long offset, long length) {
        appendCopy(BigInteger.valueOf(offset), BigInteger.valueOf(length));
    }

    private static BigInteger[] floorDivideAndRemainder(BigInteger value, BigInteger divisor) {
        BigInteger[] division = value.divideAndRemainder(divisor);
        if (division[1].signum() != 0 && division[1].signum() != divisor.signum()) {
            division[0] = division[0].subtract(BigInteger.ONE);
            division[1] = division[1].add(divisor);
        }
        return division;
    }

    private void evict() {
        BigInteger windowLength = Rope.len(rWindow);
        if (windowLength.compareTo(W) <= 0) {
            return;
        }
        BigInteger excess = windowLength.subtract(dMax);
        Rope.Split split = Rope.split(rWindow, excess, h);
        hPrefix = h.hashConcat(hPrefix, Rope.len(split.left()), Rope.hash(split.left()));
        lPrefix = lPrefix.add(excess);
        rWindow = split.right();
    }
}
