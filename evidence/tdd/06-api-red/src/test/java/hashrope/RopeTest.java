package hashrope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RopeTest {
    private static BigInteger n(long value) { return BigInteger.valueOf(value); }

    @Test void leafStoresUnsignedBytesAndImmutableMetadata() {
        byte[] bytes = {0, 127, (byte)128, (byte)255};
        Leaf leaf = new Leaf(bytes);
        assertEquals(n(4), leaf.len());
        assertEquals(BigInteger.ONE, leaf.weight());
        assertEquals(n(4461854), leaf.hashVal());
        bytes[0] = 42;
        assertEquals(0, leaf.data()[0]);
        byte[] exposed = leaf.data(); exposed[1] = 0;
        assertEquals(127, leaf.data()[1]);
    }

    @Test void leafRejectsEmptyAndNull() {
        assertEquals("Leaf cannot be empty", assertThrows(IllegalArgumentException.class, () -> new Leaf(new byte[0])).getMessage());
        assertThrows(NullPointerException.class, () -> new Leaf(null));
    }

    @Test void leafEqualityIsStructuralAndContentBased() {
        Leaf a = new Leaf(new byte[]{97});
        Leaf b = new Leaf(new byte[]{97});
        assertEquals(a,b);
        assertEquals(a.hashCode(),b.hashCode());
        assertNotEquals(a,new Leaf(new byte[]{98}));
        assertEquals(n(98),a.hashVal());
    }

    @Test void emptyRopeAndAccessors() {
        PolynomialHash h=new PolynomialHash();
        assertNull(Rope.fromBytes(new byte[0],h));
        assertEquals(BigInteger.ZERO,Rope.len(null));
        assertEquals(BigInteger.ZERO,Rope.hash(null));
        assertEquals(0,Rope.height(null));
        assertArrayEquals(new byte[0],Rope.toBytes(null));
        Node leaf=Rope.fromBytes(new byte[]{-1},h);
        assertEquals(n(1),Rope.len(leaf));
        assertEquals(n(256),Rope.hash(leaf));
        assertEquals(0,Rope.height(leaf));
    }

    @Test void internalNodeAndRepetitionMetadata() {
        PolynomialHash h=new PolynomialHash();
        Node a=new Leaf(new byte[]{97},h),b=new Leaf(new byte[]{98,99},h);
        Internal ab=new Internal(a,b,h);
        assertSame(a,ab.left()); assertSame(b,ab.right());
        assertEquals(n(3),ab.len()); assertEquals(n(2),ab.weight());
        assertEquals(h.hash(new byte[]{97,98,99}),ab.hashVal());
        assertEquals(1,Rope.height(ab));
        RepeatNode repeated=new RepeatNode(ab,n(3),h);
        assertSame(ab,repeated.child()); assertEquals(n(3),repeated.reps());
        assertEquals(n(9),repeated.len()); assertEquals(n(6),repeated.weight());
        assertEquals(2,Rope.height(repeated));
        assertArrayEquals(new byte[]{97,98,99,97,98,99,97,98,99},Rope.toBytes(repeated));
        assertEquals(h.hash(Rope.toBytes(repeated)),repeated.hashVal());
        assertEquals(ab,new Internal(new Leaf(new byte[]{97}),new Leaf(new byte[]{98,99}),h));
        assertEquals(repeated,new RepeatNode(ab,n(3),h));
        assertThrows(IllegalArgumentException.class,()->new RepeatNode(a,n(1),h));
        assertThrows(NullPointerException.class,()->new Internal(null,b,h));
    }

    @Test void repeatsRetainSharingAndUnboundedLogicalSizes() {
        PolynomialHash h=new PolynomialHash(); Node a=new Leaf(new byte[]{97,98},h);
        assertNull(Rope.repeat(a,n(0),h)); assertSame(a,Rope.repeat(a,n(1),h));
        assertNull(Rope.repeat(null,n(0),h)); assertNull(Rope.repeat(null,n(1),h));
        assertThrows(IllegalArgumentException.class,()->Rope.repeat(a,n(-1),h));
        BigInteger q=BigInteger.ONE.shiftLeft(100).add(n(7));
        Node huge=Rope.repeat(a,q,h);
        assertEquals(q.multiply(n(2)),huge.len()); assertEquals(q,huge.weight());
        assertEquals(1,Rope.height(huge));
        assertEquals(h.hashRepeat(a.hashVal(),n(2),q),huge.hashVal());
        assertThrows(ArithmeticException.class,()->Rope.toBytes(huge));
        Node nested=Rope.repeat(huge,n(2),h);
        assertSame(huge,((RepeatNode)nested).child()); assertEquals(2,Rope.height(nested));
    }

    @Test void concatenationAndSplitsPreserveReferencesAndBytes() {
        PolynomialHash h=new PolynomialHash(); Node a=new Leaf(new byte[]{1,2},h),b=new Leaf(new byte[]{3,4,5},h);
        assertSame(a,Rope.concat(a,null,h)); assertSame(b,Rope.concat(null,b,h));
        assertNull(Rope.concat(null,null,h));
        Node ab=Rope.concat(a,b,h);
        assertArrayEquals(new byte[]{1,2,3,4,5},Rope.toBytes(ab));
        assertEquals(h.hash(Rope.toBytes(ab)),ab.hashVal());
        Rope.Split boundary=Rope.split(ab,n(2),h);
        assertSame(a,boundary.left());assertSame(b,boundary.right());
        assertSame(ab,Rope.split(ab,n(-100),h).right());
        assertSame(ab,Rope.split(ab,n(100),h).left());
        assertNull(Rope.split(null,n(100),h).left());
        assertNull(Rope.split(null,n(-100),h).right());
        byte[] original=Rope.toBytes(ab);
        for(int p=0;p<=original.length;p++){
            Rope.Split split=Rope.split(ab,n(p),h);
            assertArrayEquals(Arrays.copyOfRange(original,0,p),Rope.toBytes(split.left()));
            assertArrayEquals(Arrays.copyOfRange(original,p,original.length),Rope.toBytes(split.right()));
            assertEquals(ab.hashVal(),Rope.hash(Rope.concat(split.left(),split.right(),h)));
        }
        assertArrayEquals(new byte[]{1,2},Rope.toBytes(a));
    }

    @Test void repeatSplitsHandleBoundariesInsidePeriodsAndUnboundedPositions() {
        PolynomialHash h=new PolynomialHash(); Node period=Rope.concat(new Leaf(new byte[]{1,2},h),new Leaf(new byte[]{3},h),h);
        Node repeated=Rope.repeat(period,n(7),h); byte[] raw=Rope.toBytes(repeated);
        for(int p=0;p<=raw.length;p++){
            Rope.Split s=Rope.split(repeated,n(p),h);
            assertArrayEquals(Arrays.copyOfRange(raw,0,p),Rope.toBytes(s.left()));
            assertArrayEquals(Arrays.copyOfRange(raw,p,raw.length),Rope.toBytes(s.right()));
        }
        BigInteger q=BigInteger.ONE.shiftLeft(90).add(n(5));
        Node huge=Rope.repeat(period,q,h);
        BigInteger p=q.multiply(n(2)).add(n(1));
        Rope.Split split=Rope.split(huge,p,h);
        assertEquals(p,Rope.len(split.left())); assertEquals(huge.len().subtract(p),Rope.len(split.right()));
        assertEquals(huge.hashVal(),h.hashConcat(Rope.hash(split.left()),Rope.len(split.right()),Rope.hash(split.right())));
    }

    @Test void unicodeIsExplicitlyEncodedAndSplitByBytes() {
        PolynomialHash h=new PolynomialHash(); byte[] raw="Aé😀Z".getBytes(StandardCharsets.UTF_8);
        Node root=Rope.fromBytes(raw,h);
        assertEquals(n(8),root.len());
        for(int p=0;p<=raw.length;p++){
            Rope.Split s=Rope.split(root,n(p),h);
            assertArrayEquals(Arrays.copyOfRange(raw,0,p),Rope.toBytes(s.left()));
            assertArrayEquals(Arrays.copyOfRange(raw,p,raw.length),Rope.toBytes(s.right()));
        }
    }

    @Test void validatorChecksBalanceButDoesNotRehashOrCheckContext() {
        PolynomialHash h=new PolynomialHash(); Node a=new Leaf(new byte[]{1},h);
        Rope.validate(null); Rope.validate(a);
        Node wrongContext=new Internal(a,new Leaf(new byte[]{2},h),new PolynomialHash(n(31),n(2)));
        Rope.validate(wrongContext,h);
        Node tooHeavy=new Internal(Rope.repeat(a,n(10),h),a,h);
        assertEquals("Left child too heavy: 10/11",assertThrows(AssertionError.class,()->Rope.validate(tooHeavy,h)).getMessage());
        Node tooLight=new Internal(a,Rope.repeat(a,n(10),h),h);
        assertEquals("Left child too light: 1/11",assertThrows(AssertionError.class,()->Rope.validate(tooLight,h)).getMessage());
    }

    @Test void repeatedAppendAndPrependMaintainWeightBalance() {
        PolynomialHash h=new PolynomialHash(); Node appended=null,prepended=null;
        byte[] reference=new byte[400];
        for(int k=0;k<reference.length;k++){
            reference[k]=(byte)k; Node leaf=new Leaf(new byte[]{(byte)k},h);
            appended=Rope.concat(appended,leaf,h); prepended=Rope.concat(leaf,prepended,h);
            Rope.validate(appended,h); Rope.validate(prepended,h);
        }
        assertArrayEquals(reference,Rope.toBytes(appended));
        for(int k=0;k<reference.length;k++)assertEquals(reference[reference.length-1-k],Rope.toBytes(prepended)[k]);
        assertTrue(Rope.height(appended)<20); assertTrue(Rope.height(prepended)<20);
        Node hugeLeaf=new Leaf(new byte[10000],h);
        Node combined=Rope.concat(hugeLeaf,appended,h); Rope.validate(combined,h);
        assertEquals(n(401),combined.weight());
    }

    @Test void balancingHandlesRepeatDecompositionAndPersistentSplitHistories() {
        PolynomialHash h=new PolynomialHash(); Node a=new Leaf(new byte[]{1,2},h),b=new Leaf(new byte[]{3},h);
        for(int q:new int[]{2,3,4,5,7,11,29,100,1001}){
            Node x=Rope.concat(Rope.repeat(a,n(q),h),b,h);
            Node y=Rope.concat(b,Rope.repeat(a,n(q),h),h);
            Rope.validate(x,h); Rope.validate(y,h);
            for(int p=0;p<x.len().intValueExact();p+=Math.max(1,q/9)){
                Rope.Split s=Rope.split(x,n(p),h); Rope.validate(s.left(),h); Rope.validate(s.right(),h);
                Node joined=Rope.concat(s.left(),s.right(),h); Rope.validate(joined,h);
                assertEquals(x.hashVal(),joined.hashVal());
            }
        }
        BigInteger q=BigInteger.ONE.shiftLeft(100).add(n(3));
        Node huge=Rope.concat(Rope.repeat(a,q,h),b,h); Rope.validate(huge,h);
        assertEquals(q.multiply(n(2)).add(n(1)),huge.len());
        assertTrue(Rope.height(huge)<110);
    }

    @Test void substringHashMatchesEveryValidRangeAcrossAllNodeKinds() {
        for(PolynomialHash h:new PolynomialHash[]{new PolynomialHash(),new PolynomialHash(PolynomialHash.MERSENNE_127,n(131)),new PolynomialHash(n(31),n(2))}){
            Node a=new Leaf(new byte[]{0,-1,97},h),b=new Leaf(new byte[]{-128,10},h);
            Node combined=Rope.concat(a,b,h);
            for(Node root:new Node[]{null,a,combined,Rope.repeat(combined,n(5),h),Rope.concat(Rope.repeat(a,n(7),h),b,h)}){
                byte[] raw=Rope.toBytes(root);
                for(int start=0;start<=raw.length;start++)for(int length=0;length<=raw.length-start;length++){
                    assertEquals(h.hash(Arrays.copyOfRange(raw,start,start+length)),Rope.substrHash(root,n(start),n(length),h));
                }
            }
        }
    }

    @Test void substringFastPathUsesStoredMetadataAndHandlesHugeRepeats() {
        PolynomialHash h=new PolynomialHash();Node a=new Leaf(new byte[]{97,98},h);
        assertEquals(a.hashVal(),Rope.substrHash(a,n(0),n(2),new PolynomialHash(n(31),n(2))));
        BigInteger q=BigInteger.ONE.shiftLeft(120).add(n(9));Node huge=Rope.repeat(a,q,h);
        BigInteger start=BigInteger.ONE.shiftLeft(110).add(n(1));BigInteger length=BigInteger.ONE.shiftLeft(100).add(n(3));
        BigInteger[] qr=length.divideAndRemainder(n(2));
        BigInteger expected=h.hashConcat(h.hashRepeat(h.hash(new byte[]{98,97}),n(2),qr[0]),qr[1],h.hash(new byte[]{98}));
        assertEquals(expected,Rope.substrHash(huge,start,length,h));
        assertEquals(huge.hashVal(),Rope.substrHash(huge,BigInteger.ZERO,huge.len(),h));
    }

    @Test void outOfDomainSubstringInputsRetainPythonShapeDependentResults() {
        PolynomialHash h=new PolynomialHash();
        Node leaf=new Leaf("abcdef".getBytes(StandardCharsets.US_ASCII),h);
        Node internal=Rope.concat(new Leaf("abc".getBytes(StandardCharsets.US_ASCII),h),new Leaf("def".getBytes(StandardCharsets.US_ASCII),h),h);
        Node repeat=Rope.repeat(new Leaf(new byte[]{97,98},h),n(3),h);
        assertEquals(n(12937),Rope.substrHash(leaf,n(-8),n(4),h));
        assertEquals(n(0),Rope.substrHash(internal,n(-8),n(4),h));
        assertEquals(n(222024794),Rope.substrHash(repeat,n(-8),n(4),h));
        assertEquals(new BigInteger("1993167763282484272"),Rope.substrHash(repeat,n(0),n(-1),h));
        assertEquals(n(0),Rope.substrHash(leaf,n(6),n(1),h));
        assertEquals(n(98),Rope.substrHash(repeat,n(6),n(1),h));
        assertEquals(n(0),Rope.substrHash(null,n(-9),n(10),h));
        assertEquals(n(0),Rope.substrHash(repeat,n(999),n(0),h));
    }
}
