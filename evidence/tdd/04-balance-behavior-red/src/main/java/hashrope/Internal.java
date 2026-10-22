package hashrope;

import java.math.BigInteger;
import java.util.Objects;

/** Immutable pair of nonempty subtrees. This constructor does not rebalance. */
public final class Internal implements Node {
    private final Node left, right;
    private final BigInteger len, weight, hashVal;
    /** Joins metadata directly, without balancing.
     * @param left left subtree
     * @param right right subtree
     * @param h shared hash parameters
     */
    public Internal(Node left, Node right, PolynomialHash h) {
        this.left=Objects.requireNonNull(left,"left"); this.right=Objects.requireNonNull(right,"right");
        len=left.len().add(right.len()); weight=left.weight().add(right.weight());
        hashVal=h.hashConcat(left.hashVal(),right.len(),right.hashVal());
    }
    /** Returns the shared left subtree. @return left node */
    public Node left(){return left;}
    /** Returns the shared right subtree. @return right node */
    public Node right(){return right;}
    @Override public BigInteger len(){return len;}
    @Override public BigInteger weight(){return weight;}
    @Override public BigInteger hashVal(){return hashVal;}
    @Override public boolean equals(Object o){return o instanceof Internal n && left.equals(n.left)&&right.equals(n.right)&&hashVal.equals(n.hashVal);}
    @Override public int hashCode(){return Objects.hash(left,right,hashVal);}
    @Override public String toString(){return "Internal[len="+len+", weight="+weight+", hashVal="+hashVal+"]";}
}
