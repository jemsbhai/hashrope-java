package hashrope;

import java.math.BigInteger;

/** Persistent byte-rope operations matching Python hashrope 0.2.2. */
public final class Rope {
    private Rope(){}
    /** Returns the byte length, or zero for an empty rope.
     * @param node nullable root @return byte count
     */
    public static BigInteger len(Node node){return node==null?BigInteger.ZERO:node.len();}
    /** Returns the stored hash, or zero for an empty rope.
     * @param node nullable root @return hash
     */
    public static BigInteger hash(Node node){return node==null?BigInteger.ZERO:node.hashVal();}
    /** Returns tree height; leaves and the empty rope have height zero.
     * @param node nullable root @return height
     */
    public static int height(Node node){
        if(node==null||node instanceof Leaf)return 0;
        if(node instanceof RepeatNode r)return 1+height(r.child());
        Internal i=(Internal)node;return 1+Math.max(height(i.left()),height(i.right()));
    }
    /** Builds one leaf or the empty rope.
     * @param data bytes @param h hash parameters @return root, or null
     */
    public static Node fromBytes(byte[] data,PolynomialHash h){return data.length==0?null:new Leaf(data,h);}
    /** Encodes a repetition without expanding bytes; zero returns null, one returns the same node.
     * @param node child @param count repeat count @param h hash parameters @return repeated root
     */
    public static Node repeat(Node node,BigInteger count,PolynomialHash h){
        if(count.signum()==0)return null;
        if(count.equals(BigInteger.ONE))return node;
        return new RepeatNode(node,count,h);
    }
    /** Long-count convenience overload.
     * @param node child @param count repeat count @param h hash parameters @return repeated root
     */
    public static Node repeat(Node node,long count,PolynomialHash h){return repeat(node,BigInteger.valueOf(count),h);}
    /** Materializes bytes into a fresh Java array.
     * @param node nullable root @return copied bytes
     * @throws ArithmeticException if logical length exceeds the Java int array index range
     * @throws OutOfMemoryError if the VM cannot allocate the resulting array
     */
    public static byte[] toBytes(Node node){
        byte[] result=new byte[len(node).intValueExact()];
        if(node!=null)collect(node,result,0);
        return result;
    }
    private static int collect(Node node,byte[] out,int at){
        if(node instanceof Leaf l){byte[] data=l.data();System.arraycopy(data,0,out,at,data.length);return at+data.length;}
        if(node instanceof Internal i)return collect(i.right(),out,collect(i.left(),out,at));
        RepeatNode r=(RepeatNode)node;
        int reps=r.reps().intValueExact();
        for(int n=0;n<reps;n++)at=collect(r.child(),out,at);
        return at;
    }
}
