package hashrope;

import java.math.BigInteger;
import java.util.Objects;

/** Immutable repetition with one shared child and no expansion of its bytes. */
public final class RepeatNode implements Node {
    private final Node child;
    private final BigInteger reps,len,weight,hashVal;
    /** Creates a repetition.
     *
     * @param child nonempty child
     *
     * @param reps repetition count, at least two
     *
     * @param h shared hash parameters
     * @throws IllegalArgumentException if reps is smaller than two
     */
    public RepeatNode(Node child, BigInteger reps, PolynomialHash h){
        if(reps.compareTo(BigInteger.TWO)<0) throw new IllegalArgumentException("RepeatNode reps must be >= 2, got "+reps);
        this.child=Objects.requireNonNull(child,"child"); this.reps=reps;
        len=child.len().multiply(reps); weight=child.weight().multiply(reps);
        BigInteger power=h.power(child.len());
        BigInteger phi=PolynomialHash.phi(reps,power,h.prime());
        hashVal=PolynomialHash.mersenneMul(child.hashVal(),phi,h.prime());
    }
    /** Creates a repetition with a long count.
     *
     * @param child nonempty child
     * @param reps count
     * @param h hash parameters
     */
    public RepeatNode(Node child,long reps,PolynomialHash h){this(child,BigInteger.valueOf(reps),h);}
    /** Returns the shared child.
     * @return child node */
    public Node child(){return child;}
    /** Returns the repetition count.
     * @return arbitrary-precision count */
    public BigInteger reps(){return reps;}
    @Override public BigInteger len(){return len;}
    @Override public BigInteger weight(){return weight;}
    @Override public BigInteger hashVal(){return hashVal;}
    @Override public boolean equals(Object o){return o instanceof RepeatNode n && child.equals(n.child)&&reps.equals(n.reps)&&hashVal.equals(n.hashVal);}
    @Override public int hashCode(){return Objects.hash(child,reps,hashVal);}
    @Override public String toString(){return "RepeatNode[reps="+reps+", len="+len+", hashVal="+hashVal+"]";}
}
