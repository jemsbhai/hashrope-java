/**
 * Immutable polynomial hash ropes and mutable sliding-window stream hashing.
 *
 * <p>All data and indices are bytes. Encode text explicitly; UTF-8 code points
 * may be split across byte boundaries. Hash values and compressed sequence sizes
 * use {@link java.math.BigInteger}; no signed-long ceiling is imposed. A null
 * {@link hashrope.Node} is the empty rope.
 *
 * <p>Use one {@link hashrope.PolynomialHash} context consistently throughout a
 * tree. Nodes do not carry profile identifiers. These deterministic polynomial
 * hashes are not cryptographic digests or authentication codes.
 *
 * <p>The implementation follows Python hashrope 0.2.2. See the repository
 * compatibility contract for language adaptations, invalid-input quirks and
 * materialization limits.
 */
package hashrope;
