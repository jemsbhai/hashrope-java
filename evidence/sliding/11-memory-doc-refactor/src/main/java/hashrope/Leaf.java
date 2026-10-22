package hashrope;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/** An immutable, nonempty byte leaf. Java bytes are interpreted as unsigned. */
public final class Leaf implements Node {
    private final byte[] data;
    private final BigInteger hashVal;

    /** Creates a leaf with the default polynomial hash.
     *
     * @param data bytes to copy
     */
    public Leaf(byte[] data) { this(data, new PolynomialHash()); }

    /** Creates a leaf, defensively copying its bytes.
     *
     * @param data nonempty byte sequence
     *
     * @param h hash parameters
     * @throws IllegalArgumentException if the data is empty
     */
    public Leaf(byte[] data, PolynomialHash h) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) throw new IllegalArgumentException("Leaf cannot be empty");
        this.data = data.clone();
        this.hashVal = h.hash(this.data);
    }

    /** Returns a copy of this leaf's bytes.
     * @return copied bytes */
    public byte[] data() { return data.clone(); }
    int byteLength() { return data.length; }
    byte[] slice(int start,int end) { return Arrays.copyOfRange(data,start,end); }
    void copyTo(byte[] destination,int offset) { System.arraycopy(data,0,destination,offset,data.length); }
    /** Returns the byte length.
     * @return nonzero length */
    public BigInteger len() { return BigInteger.valueOf(data.length); }
    /** Returns this leaf's weight.
     * @return one */
    public BigInteger weight() { return BigInteger.ONE; }
    /** Returns the stored hash.
     * @return polynomial hash */
    public BigInteger hashVal() { return hashVal; }
    @Override public boolean equals(Object other) {
        return other instanceof Leaf l && Arrays.equals(data,l.data) && hashVal.equals(l.hashVal);
    }
    @Override public int hashCode() { return 31*Arrays.hashCode(data)+hashVal.hashCode(); }
    @Override public String toString() { return "Leaf[len="+len()+", hashVal="+hashVal+"]"; }
}
