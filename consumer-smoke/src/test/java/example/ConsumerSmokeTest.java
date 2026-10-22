package example;

import hashrope.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumerSmokeTest {
    @Test void consumesPublishedCoordinatesWithoutSourceTreeAccess() {
        assertEquals("0.2.2", Hashrope.VERSION);
        PolynomialHash h = new PolynomialHash(PolynomialHash.MERSENNE_127, BigInteger.valueOf(131));
        byte[] data = "Aé😀Z".getBytes(StandardCharsets.UTF_8);
        Node root = Rope.fromBytes(data, h);
        Rope.Split parts = Rope.split(root, 3, h);
        assertEquals(root.hashVal(), Rope.hash(Rope.concat(parts.left(), parts.right(), h)));
        Node huge = Rope.repeat(root, BigInteger.ONE.shiftLeft(90), h);
        assertEquals(BigInteger.ONE.shiftLeft(93), huge.len());
        assertEquals(root.hashVal(), Rope.substrHash(huge, BigInteger.ONE.shiftLeft(80), root.len(), h));
        assertThrows(ArithmeticException.class, () -> Rope.toBytes(huge));
        Rope.validate(huge, h);
    }

    @Test void streamingAndIndependentExampleUseTheInstalledJar() {
        SlidingWindow stream = new SlidingWindow(8, 4);
        stream.appendBytes(new byte[]{97, 98, 99});
        stream.appendCopy(3, 12);
        assertEquals(new PolynomialHash().hash("abcabcabcabcabc".getBytes(StandardCharsets.US_ASCII)), stream.finalHash());
        assertEquals(BigInteger.valueOf(8), stream.windowLen());
        HashropeExample.main(new String[0]);
    }
}
