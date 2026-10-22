package example;

import hashrope.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/** A separate application consuming only the locally installed Hashrope artifact. */
public final class HashropeExample {
    private HashropeExample() {}

    public static void main(String[] args) {
        PolynomialHash h = new PolynomialHash();
        Node hello = Rope.fromBytes("hello ".getBytes(StandardCharsets.UTF_8), h);
        Node world = Rope.fromBytes("world".getBytes(StandardCharsets.UTF_8), h);
        Node joined = Rope.concat(hello, world, h);
        System.out.println("hello world hash: " + Rope.hash(joined));
        System.out.println("world hash: " + Rope.substrHash(joined, 6, 5, h));

        // Large logical lengths do not require allocating their represented bytes.
        Node enormous = Rope.repeat(joined, BigInteger.ONE.shiftLeft(100), h);
        System.out.println("compressed byte length: " + enormous.len());

        SlidingWindow stream = new SlidingWindow(8, 4);
        stream.appendBytes("abc".getBytes(StandardCharsets.UTF_8));
        stream.appendCopy(3, 12);
        System.out.println("stream hash: " + stream.finalHash());
        System.out.println("retained bytes: " + stream.windowLen());
    }
}
