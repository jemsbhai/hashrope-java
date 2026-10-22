package hashrope;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubclassParityTest {
    private static final class NoRepeatOverride extends PolynomialHash {
        @Override public BigInteger hashRepeat(BigInteger value,BigInteger length,BigInteger repetitions) {
            throw new AssertionError("Python does not dispatch to hash_repeat here");
        }
    }

    @Test void repeatNodeUsesPowerAndPrimitiveArithmeticNotHashRepeatOverride() {
        PolynomialHash normal=new PolynomialHash(); Node leaf=new Leaf(new byte[]{97,98},normal);
        RepeatNode repeated=new RepeatNode(leaf,BigInteger.valueOf(4),new NoRepeatOverride());
        assertEquals(normal.hash(new byte[]{97,98,97,98,97,98,97,98}),repeated.hashVal());
    }

    @Test void repeatSubstringUsesPrimitiveArithmeticNotHashRepeatOverride() {
        PolynomialHash normal=new PolynomialHash();Node leaf=new Leaf(new byte[]{97,98},normal);
        Node repeated=Rope.repeat(leaf,10,normal);
        assertEquals(normal.hash(new byte[]{98,97,98,97,98,97,98}),Rope.substrHash(repeated,1,7,new NoRepeatOverride()));
    }

    @Test void hasherOverrideCannotRetainTheLeafOwnedByteArray() {
        class CapturingHash extends PolynomialHash {
            byte[] captured;
            @Override public BigInteger hash(byte[] value){captured=value;return super.hash(value);}
        }
        CapturingHash h=new CapturingHash();byte[] original={1,2,3};Leaf leaf=new Leaf(original,h);
        assertSame(original,h.captured); // Python hashes its caller's object after copying it.
        h.captured[0]=9;
        assertArrayEquals(new byte[]{1,2,3},leaf.data());
        assertEquals(new PolynomialHash().hash(new byte[]{1,2,3}),leaf.hashVal());
    }
}
