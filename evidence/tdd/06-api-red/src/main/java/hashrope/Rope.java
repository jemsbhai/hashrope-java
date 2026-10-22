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
    /** Concatenates two ropes with BB[2/7] rebalancing, preserving empty identities.
     * @param left left root @param right right root @param h shared hash parameters @return concatenated root
     */
    public static Node concat(Node left,Node right,PolynomialHash h){
        if(left==null)return right;
        if(right==null)return left;
        return join(left,right,h);
    }
    private static boolean balanced(BigInteger wl,BigInteger wr){
        BigInteger total=wl.add(wr);
        if(total.compareTo(BigInteger.TWO)<=0)return true;
        BigInteger scaled=wl.multiply(BigInteger.valueOf(7));
        return total.multiply(BigInteger.TWO).compareTo(scaled)<=0 && scaled.compareTo(total.multiply(BigInteger.valueOf(5)))<=0;
    }
    private static Split decompose(Node node,PolynomialHash h){
        if(node instanceof Internal i)return new Split(i.left(),i.right());
        if(node instanceof RepeatNode r){
            BigInteger half=r.reps().shiftRight(1);
            return new Split(repeat(r.child(),half,h),repeat(r.child(),r.reps().subtract(half),h));
        }
        throw new IllegalArgumentException("Cannot decompose Leaf in balance");
    }
    private static Node balance(Node left,Node right,PolynomialHash h){
        BigInteger wl=left.weight(),wr=right.weight();
        if(balanced(wl,wr))return new Internal(left,right,h);
        if(wl.add(wr).multiply(BigInteger.TWO).compareTo(wl.multiply(BigInteger.valueOf(7)))>0){
            Split r=decompose(right,h);Node rl=r.left(),rr=r.right();
            if(balanced(wl,rl.weight()) && balanced(wl.add(rl.weight()),rr.weight()))
                return new Internal(new Internal(left,rl,h),rr,h);
            Split inner=decompose(rl,h);
            return balance(balance(left,inner.left(),h),balance(inner.right(),rr,h),h);
        }
        Split l=decompose(left,h);Node ll=l.left(),lr=l.right();
        if(balanced(lr.weight(),wr) && balanced(ll.weight(),lr.weight().add(wr)))
            return new Internal(ll,new Internal(lr,right,h),h);
        Split inner=decompose(lr,h);
        return balance(balance(ll,inner.left(),h),balance(inner.right(),right,h),h);
    }
    private static Node join(Node left,Node right,PolynomialHash h){
        if(balanced(left.weight(),right.weight()))return new Internal(left,right,h);
        if(left.weight().compareTo(right.weight())>0){
            if(left instanceof Internal i)return balance(i.left(),join(i.right(),right,h),h);
            if(left instanceof RepeatNode){Split l=decompose(left,h);return join(l.left(),join(l.right(),right,h),h);}
        }else{
            if(right instanceof Internal i)return balance(join(left,i.left(),h),i.right(),h);
            if(right instanceof RepeatNode){Split r=decompose(right,h);return join(join(left,r.left(),h),r.right(),h);}
        }
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
    /** Hashes a byte range without constructing rope nodes.
     * Full nodes use their stored hash. For valid queries, start and length are nonnegative
     * and their sum is at most the rope length. Out-of-domain queries preserve the pinned
     * Python release's shape-dependent behavior; they are not general Python slices.
     * @param node nullable root @param start byte offset @param length requested byte count
     * @param h shared hash parameters @return substring hash
     */
    public static BigInteger substrHash(Node node,BigInteger start,BigInteger length,PolynomialHash h){
        if(node==null||length.signum()==0)return BigInteger.ZERO;
        return hashRange(node,start,length,h);
    }
    /** Long-coordinate convenience overload.
     * @param node root @param start byte offset @param length byte count @param h hash parameters @return substring hash
     */
    public static BigInteger substrHash(Node node,long start,long length,PolynomialHash h){
        return substrHash(node,BigInteger.valueOf(start),BigInteger.valueOf(length),h);
    }
    private static BigInteger floorDiv(BigInteger a,BigInteger b){
        BigInteger[] qr=a.divideAndRemainder(b);
        return qr[1].signum()<0?qr[0].subtract(BigInteger.ONE):qr[0];
    }
    private static int sliceIndex(BigInteger value,int length){
        BigInteger n=BigInteger.valueOf(length);
        if(value.signum()<0)value=value.add(n);
        return value.max(BigInteger.ZERO).min(n).intValue();
    }
    private static BigInteger hashRange(Node node,BigInteger start,BigInteger length,PolynomialHash h){
        BigInteger p=h.prime();
        if(start.signum()==0&&length.equals(node.len()))return node.hashVal();
        if(node instanceof Leaf leaf){
            byte[] data=leaf.data();int from=sliceIndex(start,data.length),to=sliceIndex(start.add(length),data.length);
            return h.hash(Arrays.copyOfRange(data,from,Math.max(from,to)));
        }
        if(node instanceof Internal i){
            BigInteger ll=i.left().len();
            if(start.add(length).compareTo(ll)<=0)return hashRange(i.left(),start,length,h);
            if(start.compareTo(ll)>=0)return hashRange(i.right(),start.subtract(ll),length,h);
            BigInteger leftLength=ll.subtract(start),rightLength=length.subtract(leftLength);
            return h.hashConcat(hashRange(i.left(),start,leftLength,h),rightLength,hashRange(i.right(),BigInteger.ZERO,rightLength,h));
        }
        RepeatNode r=(RepeatNode)node;BigInteger d=r.child().len();
        BigInteger firstCopy=floorDiv(start,d),startInCopy=start.mod(d);
        BigInteger end=start.add(length).subtract(BigInteger.ONE),lastCopy=floorDiv(end,d),endInCopy=end.mod(d);
        if(firstCopy.equals(lastCopy))return hashRange(r.child(),startInCopy,length,h);
        BigInteger tail=hashRange(r.child(),startInCopy,d.subtract(startInCopy),h);
        BigInteger fullCopies=lastCopy.subtract(firstCopy).subtract(BigInteger.ONE);
        BigInteger full=fullCopies.signum()>0?h.hashRepeat(r.child().hashVal(),d,fullCopies):BigInteger.ZERO;
        BigInteger fullLength=fullCopies.multiply(d),headLength=endInCopy.add(BigInteger.ONE);
        BigInteger head=hashRange(r.child(),BigInteger.ZERO,headLength,h);
        return PolynomialHash.mersenneMod(h.power(fullLength.add(headLength)).multiply(tail)
                .add(h.power(headLength).multiply(full)).add(head),p);
    }
    /** Checks structural length, weight, repetition and BB[2/7] invariants.
     * Hashes are not recomputed, matching the Python validator.
     * @param node nullable root
     * @throws AssertionError if an invariant fails, independent of the JVM assertion flag
     */
    public static void validate(Node node){validate(node,null);}
    /** Checks structural invariants; the hash argument is intentionally unused.
     * @param node nullable root @param h optional hash context, unused for compatibility
     * @throws AssertionError if an invariant fails
     */
    public static void validate(Node node,PolynomialHash h){
        if(node==null)return;
        if(node instanceof Leaf l){
            check(l.weight().equals(BigInteger.ONE),"Leaf weight must be 1, got "+l.weight());
            check(l.len().equals(BigInteger.valueOf(l.data().length)),"Leaf len mismatch");return;
        }
        if(node instanceof Internal i){
            check(i.len().equals(i.left().len().add(i.right().len())),"Internal len mismatch");
            check(i.weight().equals(i.left().weight().add(i.right().weight())),"Internal weight mismatch");
            BigInteger total=i.weight(),wl=i.left().weight();
            if(total.compareTo(BigInteger.TWO)>0){
                check(total.multiply(BigInteger.TWO).compareTo(wl.multiply(BigInteger.valueOf(7)))<=0,"Left child too light: "+wl+"/"+total);
                check(wl.multiply(BigInteger.valueOf(7)).compareTo(total.multiply(BigInteger.valueOf(5)))<=0,"Left child too heavy: "+wl+"/"+total);
            }
            validate(i.left(),h);validate(i.right(),h);return;
        }
        RepeatNode r=(RepeatNode)node;
        check(r.len().equals(r.child().len().multiply(r.reps())),"RepeatNode len mismatch");
        check(r.weight().equals(r.child().weight().multiply(r.reps())),"RepeatNode weight mismatch");
        check(r.reps().compareTo(BigInteger.TWO)>=0,"RepeatNode reps must be >= 2, got "+r.reps());
        validate(r.child(),h);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
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
