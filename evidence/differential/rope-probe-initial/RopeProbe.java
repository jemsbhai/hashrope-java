import hashrope.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Early rope oracle probe while SlidingWindow is completed independently. */
public class RopeProbe {
    public static void main(String[] args) throws Exception {
        int count=0,failed=0;
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            if(line.startsWith("#")||line.isBlank())continue;
            String[] r=line.split("\t",-1);
            if(!Set.of("rope","split","substr").contains(r[0]))continue;
            count++;
            try {
                PolynomialHash h=new PolynomialHash(n(r[2]),n(r[3]));
                Node root=build(r[4],h);
                switch(r[0]) {
                    case "rope" -> {metadata(root,r,5); Rope.validate(root,h);}
                    case "split" -> {
                        Rope.Split split=Rope.split(root,n(r[5]),h);
                        metadata(split.left(),r,6); metadata(split.right(),r,11);
                        Rope.validate(split.left(),h);Rope.validate(split.right(),h);
                    }
                    case "substr" -> equal(n(r[7]),Rope.substrHash(root,n(r[5]),n(r[6]),h));
                }
            } catch(Throwable e) {
                System.out.println(r[0]+" "+r[1]+" failed: "+e); failed++;
                if(failed<=5)e.printStackTrace(System.out);
            }
        }
        System.out.println("Rope fixture rows="+count+", failed="+failed);
        if(failed!=0)throw new AssertionError("Rope parity failures: "+failed);
    }
    static Node build(String spec,PolynomialHash h) {
        List<Node> nodes=new ArrayList<>();
        for(String token:spec.split(","))switch(token.charAt(0)) {
            case 'E' -> nodes.add(null);
            case 'L' -> nodes.add(new Leaf(bytes(token.substring(1)),h));
            case 'C','I' -> {
                Node right=nodes.remove(nodes.size()-1),left=nodes.remove(nodes.size()-1);
                nodes.add(token.equals("C")?Rope.concat(left,right,h):new Internal(left,right,h));
            }
            case 'R','N' -> {
                Node child=nodes.remove(nodes.size()-1);
                nodes.add(token.charAt(0)=='R'?Rope.repeat(child,n(token.substring(1)),h):new RepeatNode(child,n(token.substring(1)),h));
            }
            default -> throw new AssertionError(token);
        }
        equal(1,nodes.size());return nodes.get(0);
    }
    static void metadata(Node node,String[] r,int off) {
        equal(n(r[off]),Rope.len(node));
        equal(n(r[off+1]),node==null?BigInteger.ZERO:node.weight());
        equal(n(r[off+2]),Rope.hash(node));equal(Integer.parseInt(r[off+3]),Rope.height(node));
        if(!r[off+4].equals("-")&&!Arrays.equals(bytes(r[off+4]),Rope.toBytes(node)))throw new AssertionError("bytes mismatch");
    }
    static void equal(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("expected="+expected+" actual="+actual);}
    static BigInteger n(String s){return new BigInteger(s);}
    static byte[] bytes(String s){return HexFormat.of().parseHex(s);}
}
