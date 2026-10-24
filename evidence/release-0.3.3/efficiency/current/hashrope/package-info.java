/**
 * Immutable polynomial hash ropes and mutable sliding-window stream hashing.
 *
 * <p>All data and indices are bytes. Encode text explicitly; UTF-8 code points
 * may be split across byte boundaries. Hash values and compressed sequence sizes
 * use {@link java.math.BigInteger}; no signed-long ceiling is imposed. A null
 * {@link hashrope.Node} is the empty rope.
 *
 * <p>Use one {@link hashrope.PolynomialHash} context consistently throughout a
 * tree. Immutable node profiles reject incompatible composition. These deterministic polynomial
 * hashes are not cryptographic digests or authentication codes.
 *
 * <p>Exact content comparison uses {@link hashrope.Rope#equal} and
 * {@link hashrope.Rope#lcp}, independently of fingerprints. Existing arithmetic
 * retains the pinned Python hashrope 0.2.2 values. See the repository
 * compatibility contract for language adaptations, invalid-input quirks and
 * materialization limits.
 */
package hashrope;
