package hashrope;

import java.math.BigInteger;
import java.util.Arrays;

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
    /** Concatenates two ropes, preserving empty identities.
     * @param left left root @param right right root @param h shared hash parameters @return concatenated root
     */
    public static Node concat(Node left,Node right,PolynomialHash h){
        if(left==null)return right;
        if(right==null)return left;
        return new Internal(left,right,h);
    }
    /** The persistent halves of a split. Either root may be null.
     * @param left prefix @param right suffix
     */
    public record Split(Node left,Node right){}
    /** Splits at a byte position, clamping to the two endpoints.
     * @param node root @param pos byte position @param h shared hash parameters @return persistent halves
     */
    public static Split split(Node node,BigInteger pos,PolynomialHash h){
        if(node==null)return new Split(null,null);
        if(pos.signum()<=0)return new Split(null,node);
        if(pos.compareTo(node.len())>=0)return new Split(node,null);
        if(node instanceof Leaf l){
            byte[] data=l.data();int p=pos.intValueExact();
            return new Split(new Leaf(Arrays.copyOfRange(data,0,p),h),new Leaf(Arrays.copyOfRange(data,p,data.length),h));
        }
        if(node instanceof Internal i){
            int cmp=pos.compareTo(i.left().len());
            if(cmp==0)return new Split(i.left(),i.right());
            if(cmp<0){Split s=split(i.left(),pos,h);return new Split(s.left(),concat(s.right(),i.right(),h));}
            Split s=split(i.right(),pos.subtract(i.left().len()),h);
            return new Split(concat(i.left(),s.left(),h),s.right());
        }
        RepeatNode r=(RepeatNode)node;
        BigInteger[] qr=pos.divideAndRemainder(r.child().len());
        if(qr[1].signum()==0)return new Split(repeat(r.child(),qr[0],h),repeat(r.child(),r.reps().subtract(qr[0]),h));
        Split s=split(r.child(),qr[1],h);
        Node left=concat(repeat(r.child(),qr[0],h),s.left(),h);
        Node right=concat(s.right(),repeat(r.child(),r.reps().subtract(qr[0]).subtract(BigInteger.ONE),h),h);
        return new Split(left,right);
    }
    /** Long-position convenience overload.
     * @param node root @param pos byte position @param h hash parameters @return persistent halves
     */
    public static Split split(Node node,long pos,PolynomialHash h){return split(node,BigInteger.valueOf(pos),h);}
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
