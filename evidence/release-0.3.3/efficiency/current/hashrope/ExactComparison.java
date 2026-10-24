package hashrope;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Byte proofs over virtual ranges. Never reads fingerprint metadata. */
final class ExactComparison {
    private ExactComparison() {}
    private interface Task {}
    private record Range(Node a, BigInteger aStart, Node b, BigInteger bStart,
                         BigInteger size) implements Task {}
    private record Skip(BigInteger size) implements Task {}
    private record Commit(Key key) implements Task {}

    // Node.equals/hashCode are structural and recursive. Cache keys must use
    // reference identity, including for independently allocated equal DAGs.
    private static final class Key {
        final Range range;
        Key(Range range) { this.range = range; }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Key other)) return false;
            Range x = range, y = other.range;
            return x.a == y.a && x.b == y.b && x.aStart.equals(y.aStart)
                    && x.bStart.equals(y.bStart) && x.size.equals(y.size);
        }
        @Override public int hashCode() {
            int h = 31 * System.identityHashCode(range.a) + System.identityHashCode(range.b);
            h = 31 * h + range.aStart.hashCode();
            h = 31 * h + range.bStart.hashCode();
            return 31 * h + range.size.hashCode();
        }
    }

    private record View(Node node, BigInteger start) {}
    private static View normalize(Node node, BigInteger start, BigInteger size) {
        while (true) {
            if (node instanceof Internal internal) {
                BigInteger left = internal.left().len();
                if (start.compareTo(left) >= 0) {
                    start = start.subtract(left);
                    node = internal.right();
                } else if (start.add(size).compareTo(left) <= 0) {
                    node = internal.left();
                } else return new View(node, start);
            } else if (node instanceof RepeatNode repeat) {
                BigInteger period = repeat.child().len();
                start = start.mod(period);
                if (start.add(size).compareTo(period) <= 0) node = repeat.child();
                else return new View(node, start);
            } else return new View(node, start);
        }
    }

    // A structural period can also be encoded as concat(x,x), repeatedly.
    // Identity is sufficient proof; equal fingerprints are never considered.
    private static Node period(Node node) {
        while (true) {
            if (node instanceof RepeatNode repeat) node = repeat.child();
            else if (node instanceof Internal internal && internal.left() == internal.right()) {
                node = internal.left();
            } else return node;
        }
    }

    static BigInteger lcp(Node a, BigInteger aStart, Node b, BigInteger bStart, BigInteger size) {
        if (size.signum() == 0 || (a == b && aStart.equals(bStart))) return size;
        ArrayDeque<Task> pending = new ArrayDeque<>();
        Set<Key> verified = new HashSet<>();
        pending.push(new Range(a, aStart, b, bStart, size));
        BigInteger matched = BigInteger.ZERO;

        // Once per query, reduce purely structural periodic inputs. Repeating
        // this scan at every subtree would penalize a DAG with one late edit.
        Node pNode = period(a), qNode = period(b);
        if (pNode != a || qNode != b) {
            BigInteger p = pNode.len(), q = qNode.len();
            BigInteger witness = p.add(q).subtract(p.gcd(q));
            if (witness.compareTo(size) < 0) {
                pending.clear();
                pending.push(new Skip(size.subtract(witness)));
                pending.push(new Range(a, aStart, b, bStart, witness));
            }
        }

        while (!pending.isEmpty()) {
            Task task = pending.pop();
            if (task instanceof Skip skip) {
                matched = matched.add(skip.size);
                continue;
            }
            if (task instanceof Commit commit) {
                verified.add(commit.key);
                continue;
            }
            Range range = (Range) task;
            if (range.size.signum() == 0) continue;
            View left = normalize(range.a, range.aStart, range.size);
            View right = normalize(range.b, range.bStart, range.size);
            range = new Range(left.node, left.start, right.node, right.start, range.size);
            if (left.node == right.node && left.start.equals(right.start)) {
                matched = matched.add(range.size);
                continue;
            }
            if (left.node instanceof Leaf x && right.node instanceof Leaf y) {
                int length = range.size.intValueExact();
                int common = x.commonPrefix(left.start.intValueExact(), y, right.start.intValueExact(), length);
                matched = matched.add(BigInteger.valueOf(common));
                if (common != length) return matched;
                continue;
            }
            Key key = new Key(range);
            if (verified.contains(key)) {
                matched = matched.add(range.size);
                continue;
            }
            // Equality is cached only after every preceding child/witness has
            // succeeded. A mismatch exits before these commit/skip tasks run.
            pending.push(new Commit(key));
            if (left.node instanceof RepeatNode x && right.node instanceof RepeatNode y) {
                BigInteger p = x.child().len(), q = y.child().len();
                BigInteger witness = p.add(q).subtract(p.gcd(q));
                if (witness.compareTo(range.size) < 0) {
                    pending.push(new Skip(range.size.subtract(witness)));
                    pending.push(new Range(left.node, left.start, right.node, right.start, witness));
                    continue;
                }
            }
            // Reveal Internal children before cutting at a Repeat period; the
            // opposite order can expand a giant repeated span one copy at a time.
            BigInteger cut = range.size;
            if (left.node instanceof Internal x) cut = cut.min(x.left().len().subtract(left.start));
            if (right.node instanceof Internal y) cut = cut.min(y.left().len().subtract(right.start));
            if (cut.equals(range.size)) {
                if (left.node instanceof RepeatNode x) cut = cut.min(x.child().len().subtract(left.start));
                if (right.node instanceof RepeatNode y) cut = cut.min(y.child().len().subtract(right.start));
            }
            pending.push(new Range(left.node, left.start.add(cut), right.node,
                    right.start.add(cut), range.size.subtract(cut)));
            pending.push(new Range(left.node, left.start, right.node, right.start, cut));
        }
        return matched;
    }
}
