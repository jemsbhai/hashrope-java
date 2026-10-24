package example;

import hashrope.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumerSmokeTest {
    @Test void exactApisAndProfilesAreAvailableFromTheInstalledArtifact() {
        PolynomialHash h = new PolynomialHash();
        Node a = Rope.fromBytes(new byte[]{0, (byte)131}, h);
        Node b = Rope.fromBytes(new byte[]{1, 0}, h);
        assertEquals(Rope.hash(a), Rope.hash(b));
        assertFalse(Rope.equal(a, b));
        assertEquals(BigInteger.ZERO, Rope.lcp(a, b));
        assertTrue(Rope.rangeEqual(a, 0, a, 0, 2));
        assertEquals(new HashProfile(PolynomialHash.MERSENNE_61, BigInteger.valueOf(131)), a.profile());
    }
    @Test void consumesPublishedCoordinatesWithoutSourceTreeAccess() {
        assertEquals("0.3.3", Hashrope.VERSION);
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
