import hashrope.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

/** Standalone retained-API diagnostic; compiled unchanged against each version. */
public final class ReleaseEfficiency {
    private static volatile BigInteger sink;
    private static final PolynomialHash H = new PolynomialHash();
    private static final byte[] DATA = new byte[4096];
    private static final byte[] STREAM = new byte[256];

    private static BigInteger edit(Node root) {
        Rope.Split split = Rope.split(root, 2033, H);
        return Rope.hash(Rope.concat(split.left(), split.right(), H));
    }
    private static BigInteger stream() {
        SlidingWindow window = new SlidingWindow(64, 16);
        for (int i = 0; i < STREAM.length; i += 8) {
            window.appendBytes(Arrays.copyOfRange(STREAM, i, i + 8));
        }
        return window.currentHash();
    }
    private static void measure(String name, Supplier<BigInteger> action, BigInteger expected, int repetitions) {
        // Every iteration is oracle-checked after its timed block. These sums
        // consume all answers without making their equality checks part of timing.
        for (int i = 0; i < 128; i++) sink = action.get();
        if (!expected.equals(sink)) throw new AssertionError(name + " warmup oracle");
        BigInteger expectedSum = expected.multiply(BigInteger.valueOf(repetitions));
        for (int sample = 0; sample < 7; sample++) {
            BigInteger sum = BigInteger.ZERO;
            long start = System.nanoTime();
            for (int i = 0; i < repetitions; i++) sum = sum.add(action.get());
            long elapsed = System.nanoTime() - start;
            sink = sum;
            if (!expectedSum.equals(sum)) throw new AssertionError(name + " sample oracle");
            System.out.println(name + "\t" + sample + "\t" + repetitions + "\t" + elapsed);
        }
    }
    public static void main(String[] arguments) {
        new Random(0x5e7b983L).nextBytes(DATA);
        System.arraycopy(DATA, 0, STREAM, 0, STREAM.length);
        Node root = Rope.fromBytes(DATA, H);
        BigInteger expected = H.hash(DATA), streamExpected = H.hash(STREAM);
        int rotation = Integer.parseInt(arguments[0]);
        for (int step = 0; step < 4; step++) {
            switch ((rotation + step) % 4) {
                case 0 -> measure("hash_4k", () -> H.hash(DATA), expected, 32);
                case 1 -> measure("construct_4k", () -> Rope.hash(Rope.fromBytes(DATA, H)), expected, 32);
                case 2 -> measure("split_rejoin_4k", () -> edit(root), expected, 32);
                case 3 -> measure("sliding_256", ReleaseEfficiency::stream, streamExpected, 16);
                default -> throw new AssertionError();
            }
        }
    }
}
