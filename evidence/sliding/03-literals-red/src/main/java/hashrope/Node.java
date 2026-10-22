package hashrope;

import java.math.BigInteger;

/** Immutable rope node. A {@code null} reference represents the empty rope.
 * Logical sizes are arbitrary precision; only materialized byte arrays have Java array limits.
 */
public sealed interface Node permits Leaf, Internal, RepeatNode {
    /** Returns the represented byte length. @return arbitrary-precision byte count */
    BigInteger len();
    /** Returns the expanded leaf weight. @return arbitrary-precision weight */
    BigInteger weight();
    /** Returns the stored polynomial hash. @return hash value */
    BigInteger hashVal();
}
