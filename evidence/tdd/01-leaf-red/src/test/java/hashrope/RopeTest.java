package hashrope;

import java.math.BigInteger;
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
}
