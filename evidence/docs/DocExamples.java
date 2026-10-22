import hashrope.Node;
import hashrope.PolynomialHash;
import hashrope.Rope;
import hashrope.SlidingWindow;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
public class DocExamples {
    static void example0() {
        
        PolynomialHash h = new PolynomialHash();
        Node hello = Rope.fromBytes("hello ".getBytes(StandardCharsets.UTF_8), h);
        Node world = Rope.fromBytes("world".getBytes(StandardCharsets.UTF_8), h);
        Node joined = Rope.concat(hello, world, h);
        
        if (!Rope.hash(joined).equals(h.hash("hello world".getBytes(StandardCharsets.UTF_8)))) {
            throw new AssertionError("hash mismatch");
        }
        Rope.Split halves = Rope.split(joined, 6, h);
        byte[] prefix = Rope.toBytes(halves.left()); // hello followed by a space
        byte[] suffix = Rope.toBytes(halves.right()); // world
        Rope.validate(joined);
    }
    static void example1() {
        
        PolynomialHash h = new PolynomialHash();
        Node pattern = Rope.fromBytes(new byte[] {0, (byte) 255, 65}, h);
        BigInteger copies = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(7));
        Node repeated = Rope.repeat(pattern, copies, h);
        
        BigInteger totalBytes = Rope.len(repeated); // 3 * copies, exactly
        Rope.Split halves = Rope.split(repeated, totalBytes.divide(BigInteger.TWO), h);
        BigInteger sampleHash = Rope.substrHash(
            repeated, totalBytes.subtract(BigInteger.valueOf(20)), BigInteger.valueOf(17), h);
        Rope.validate(halves.left());
        Rope.validate(halves.right());
    }
    static void example2() {
        
        SlidingWindow stream = new SlidingWindow(4, 3);
        stream.appendBytes("abc".getBytes(StandardCharsets.UTF_8));
        stream.appendCopy(3, 7); // append abcabca, including overlap
        
        PolynomialHash h = new PolynomialHash();
        if (!stream.currentHash().equals(h.hash("abcabcabca".getBytes(StandardCharsets.UTF_8)))) {
            throw new AssertionError("stream hash mismatch");
        }
        // Ten total bytes exceeded capacity seven; the retained window now has four bytes.
        System.out.println(stream.pos());       // 10
        System.out.println(stream.windowLen()); // 4
    }
    static void example3() {
        
        PolynomialHash h = new PolynomialHash();
        byte[] encoded = "A\u00e9\ud83d\ude00Z".getBytes(StandardCharsets.UTF_8);
        Node text = Rope.fromBytes(encoded, h);
        Rope.Split insideCharacter = Rope.split(text, 2, h);
        // The first two bytes are 0x41, 0xc3: the split falls inside the UTF-8 encoding of é.
        // Concatenating the halves restores the exact original bytes.
        Node restored = Rope.concat(insideCharacter.left(), insideCharacter.right(), h);
        if (!Rope.hash(restored).equals(h.hash(encoded))) throw new AssertionError();
    }
    static void example4() {
        
        PolynomialHash h127 = new PolynomialHash(
            PolynomialHash.MERSENNE_127, BigInteger.valueOf(257));
    }
    public static void main(String[] args) {
        example0();
        example1();
        example2();
        example3();
        example4();
        System.out.println("README examples completed: 5");
    }
}
