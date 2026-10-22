package hashrope;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Differential oracle: every expected result comes from the pinned Python wheel. */
class DifferentialTest {
    private static final List<String[]> GOLDEN = readRows("golden.tsv");
    private static final List<String[]> ERRORS = readRows("errors.tsv");
    private static final Map<String,List<String[]>> TRACES = slidingTraces();

    @Test void fixtureFilesAreThePinnedOracleAndEveryRowIsPresent() throws Exception {
        assertEquals(10451,GOLDEN.size());
        assertEquals(93,ERRORS.size());
        assertEquals("a0c18d9a5ea1ab06179e8818a0166d06974c1ba6be55d349604ae16bb28365da",sha256("golden.tsv"));
        assertEquals("02bb1dbd7781b9a6af566124da04f6cca6b26808a77e66416f2da912ecc19290",sha256("errors.tsv"));
        assertEquals(GOLDEN.size(),GOLDEN.stream().map(r->r[0]+":"+r[1]).distinct().count());
        assertEquals(ERRORS.size(),ERRORS.stream().map(r->r[1]).distinct().count());
    }

    @TestFactory Stream<DynamicTest> publishedPythonGoldenResults() {
        return GOLDEN.stream().map(row -> DynamicTest.dynamicTest(row[0]+" / "+row[1],()->verify(row)));
    }

    @TestFactory Stream<DynamicTest> publishedPythonErrors() {
        return ERRORS.stream().map(row -> DynamicTest.dynamicTest(row[1],()->verifyError(row)));
    }

    private static void verify(String[] r) {
        PolynomialHash h = hasher(r);
        switch(r[0]) {
            case "hash" -> assertEquals(n(r[5]),h.hash(bytes(r[4])));
            case "power" -> assertEquals(n(r[5]),h.power(n(r[4])));
            case "phi" -> assertEquals(n(r[6]),PolynomialHash.phi(n(r[4]),n(r[5]),h.prime()));
            case "mod" -> assertEquals(n(r[5]),PolynomialHash.mersenneMod(n(r[4]),h.prime()));
            case "mul" -> assertEquals(n(r[6]),PolynomialHash.mersenneMul(n(r[4]),n(r[5]),h.prime()));
            case "concatHash" -> assertEquals(n(r[7]),h.hashConcat(n(r[4]),n(r[5]),n(r[6])));
            case "repeatHash" -> assertEquals(n(r[7]),h.hashRepeat(n(r[4]),n(r[5]),n(r[6])));
            case "overlapHash" -> assertEquals(n(r[8]),h.hashOverlap(n(r[4]),n(r[5]),n(r[6]),n(r[7])));
            case "rope" -> {
                Node node=build(r[4],h);
                metadata(node,r,5);
                Rope.validate(node,h);
            }
            case "split" -> {
                Node node=build(r[4],h);
                BigInteger originalHash=Rope.hash(node), originalLen=Rope.len(node);
                Rope.Split halves=Rope.split(node,n(r[5]),h);
                metadata(halves.left(),r,6);
                metadata(halves.right(),r,11);
                Rope.validate(halves.left(),h);
                Rope.validate(halves.right(),h);
                assertEquals(originalHash,Rope.hash(node),"persistent source hash");
                assertEquals(originalLen,Rope.len(node),"persistent source length");
                assertEquals(originalHash,h.hashConcat(Rope.hash(halves.left()),Rope.len(halves.right()),Rope.hash(halves.right())));
            }
            case "substr" -> assertEquals(n(r[7]),Rope.substrHash(build(r[4],h),n(r[5]),n(r[6]),h));
            case "sliding" -> verifySliding(r);
            default -> fail("Unknown oracle record kind: "+r[0]);
        }
    }

    private static void verifySliding(String[] expected) {
        SlidingWindow window=new SlidingWindow(n(expected[6]),n(expected[7]),n(expected[2]),n(expected[3]));
        int step=Integer.parseInt(expected[5]);
        List<String[]> trace=TRACES.get(expected[4]);
        for(int i=0;i<=step;i++) {
            String[] r=trace.get(i);
            assertEquals(i,Integer.parseInt(r[5]),"trace must have contiguous steps");
            switch(r[8]) {
                case "init" -> { }
                case "bytes" -> window.appendBytes(bytes(r[9]));
                case "copy" -> window.appendCopy(n(r[9]),n(r[10]));
                default -> fail("Unknown stream operation: "+r[8]);
            }
        }
        assertEquals(n(expected[11]),window.pos(),"stream position");
        assertEquals(n(expected[12]),window.lPrefix(),"evicted prefix length");
        assertEquals(n(expected[13]),window.hPrefix(),"evicted prefix hash");
        assertEquals(n(expected[14]),window.windowLen(),"window length");
        assertEquals(n(expected[15]),Rope.hash(window.window()),"window hash");
        assertArrayEquals(bytes(expected[16]),Rope.toBytes(window.window()),"window bytes");
        assertEquals(n(expected[17]),window.currentHash(),"current stream hash");
        assertEquals(n(expected[18]),window.finalHash(),"final stream hash");
        assertEquals(Integer.parseInt(expected[19]),Rope.height(window.window()),"window height");
        assertEquals(n(expected[20]),window.window()==null?BigInteger.ZERO:window.window().weight(),"window weight");
        Rope.validate(window.window(),window.h());
    }

    private static void verifyError(String[] r) {
        Class<? extends Throwable> expectedType=switch(r[9]) {
            case "AssertionError" -> AssertionError.class;
            case "AttributeError", "TypeError" -> NullPointerException.class;
            case "ZeroDivisionError" -> ArithmeticException.class;
            case "ValueError" -> IllegalArgumentException.class;
            default -> throw new AssertionError("Unmapped Python exception: "+r[9]);
        };
        Throwable thrown=assertThrows(expectedType,()->invokeError(r));
        // Constructor/argument diagnostics are library text and match exactly.
        // VM null/arithmetic diagnostics are runtime-specific and map by type.
        if(r[9].equals("AssertionError") || r[9].equals("ValueError")) {
            assertEquals(r[10],thrown.getMessage(),"published error message");
        }
    }

    private static void invokeError(String[] r) {
        if(r[4].equals("constructor")) { hasher(r); return; }
        PolynomialHash h=hasher(r);
        switch(r[4]) {
            case "leaf" -> new Leaf(bytes(r[5]),h);
            case "repeatNode" -> new RepeatNode(build(r[5],h),n(r[6]),h);
            case "repeat" -> Rope.repeat(build(r[5],h),n(r[6]),h);
            case "internal" -> new Internal(build(r[5],h),build(r[6],h),h);
            case "overlapHash" -> h.hashOverlap(n(r[5]),n(r[6]),n(r[7]),n(r[8]));
            case "validate" -> Rope.validate(build(r[5],h),h);
            case "substr" -> Rope.substrHash(build(r[5],h),n(r[6]),n(r[7]),h);
            case "power" -> h.power(n(r[5]));
            case "slidingConstructor" -> new SlidingWindow(n(r[5]),n(r[6]),h.prime(),h.base());
            case "slidingCopy" -> {
                SlidingWindow w=new SlidingWindow(n(r[5]),n(r[6]),h.prime(),h.base());
                w.appendBytes(bytes(r[7]));
                String[] copy=r[8].split(":",-1);
                w.appendCopy(n(copy[0]),n(copy[1]));
            }
            default -> fail("Unknown oracle error operation: "+r[4]);
        }
    }

    private static Node build(String specification,PolynomialHash h) {
        // ArrayList deliberately supports null, the reference empty rope.
        List<Node> stack=new ArrayList<>();
        for(String token:specification.split(",",-1)) {
            switch(token.charAt(0)) {
                case 'E' -> stack.add(null);
                case 'L' -> stack.add(new Leaf(bytes(token.substring(1)),h));
                case 'C', 'I' -> {
                    Node right=stack.remove(stack.size()-1), left=stack.remove(stack.size()-1);
                    stack.add(token.equals("C")?Rope.concat(left,right,h):new Internal(left,right,h));
                }
                case 'R', 'N' -> {
                    Node child=stack.remove(stack.size()-1);
                    BigInteger repetitions=n(token.substring(1));
                    stack.add(token.charAt(0)=='R'?Rope.repeat(child,repetitions,h):new RepeatNode(child,repetitions,h));
                }
                default -> throw new AssertionError("Unknown rope token: "+token);
            }
        }
        assertEquals(1,stack.size(),"valid postfix fixture");
        return stack.get(0);
    }

    private static void metadata(Node node,String[] row,int offset) {
        assertEquals(n(row[offset]),Rope.len(node),"byte length");
        assertEquals(n(row[offset+1]),node==null?BigInteger.ZERO:node.weight(),"weight");
        assertEquals(n(row[offset+2]),Rope.hash(node),"hash");
        assertEquals(Integer.parseInt(row[offset+3]),Rope.height(node),"height");
        if(!row[offset+4].equals("-")) assertArrayEquals(bytes(row[offset+4]),Rope.toBytes(node),"bytes");
    }

    private static PolynomialHash hasher(String[] r) { return new PolynomialHash(n(r[2]),n(r[3])); }
    private static BigInteger n(String value) { return new BigInteger(value); }
    private static byte[] bytes(String hex) { return HexFormat.of().parseHex(hex); }

    private static List<String[]> readRows(String filename) {
        String text=new String(resource(filename),StandardCharsets.UTF_8);
        return text.lines().filter(line->!line.isBlank()&&!line.startsWith("#"))
            .map(line->line.split("\t",-1)).toList();
    }

    private static byte[] resource(String filename) {
        try(InputStream input=DifferentialTest.class.getResourceAsStream("/fixtures/"+filename)) {
            if(input==null) throw new AssertionError("Missing fixture resource: "+filename);
            return input.readAllBytes();
        } catch(IOException error) { throw new ExceptionInInitializerError(error); }
    }

    private static String sha256(String filename) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(filename)));
    }

    private static Map<String,List<String[]>> slidingTraces() {
        Map<String,List<String[]>> traces=new LinkedHashMap<>();
        for(String[] row:GOLDEN) if(row[0].equals("sliding")) traces.computeIfAbsent(row[4],ignored->new ArrayList<>()).add(row);
        return traces;
    }
}
