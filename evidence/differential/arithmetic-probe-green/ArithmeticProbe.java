import hashrope.PolynomialHash;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.HexFormat;

/** Early differential probe, run before the rope APIs are complete. */
public class ArithmeticProbe {
    public static void main(String[] args) throws Exception {
        int count=0,failed=0;
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            if(line.startsWith("#")||line.isBlank()) continue;
            String[] r=line.split("\t",-1);
            PolynomialHash h=new PolynomialHash(n(r[2]),n(r[3]));
            BigInteger actual=switch(r[0]) {
                case "hash" -> h.hash(HexFormat.of().parseHex(r[4]));
                case "power" -> h.power(n(r[4]));
                case "phi" -> PolynomialHash.phi(n(r[4]),n(r[5]),h.prime());
                case "mod" -> PolynomialHash.mersenneMod(n(r[4]),h.prime());
                case "mul" -> PolynomialHash.mersenneMul(n(r[4]),n(r[5]),h.prime());
                case "concatHash" -> h.hashConcat(n(r[4]),n(r[5]),n(r[6]));
                case "repeatHash" -> h.hashRepeat(n(r[4]),n(r[5]),n(r[6]));
                case "overlapHash" -> h.hashOverlap(n(r[4]),n(r[5]),n(r[6]),n(r[7]));
                default -> null;
            };
            if(actual==null) continue;
            count++;
            BigInteger expected=n(r[r.length-1]);
            if(!expected.equals(actual)) {System.out.println(r[0]+" "+r[1]+" expected="+expected+" actual="+actual);failed++;}
        }
        System.out.println("Arithmetic fixture rows="+count+", failed="+failed);
        if(failed!=0) throw new AssertionError("Arithmetic parity failures: "+failed);
    }
    static BigInteger n(String s){return new BigInteger(s);}
}
