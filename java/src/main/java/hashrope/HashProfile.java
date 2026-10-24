package hashrope;

import java.math.BigInteger;
import java.util.Objects;

/** Immutable polynomial fingerprint parameters. Matching profiles do not prove equal bytes.
 * @param prime modulus
 * @param base polynomial base
 */
public record HashProfile(BigInteger prime, BigInteger base) {
    /** Creates a parameter identity.
     * @param prime modulus
     * @param base polynomial base
     */
    public HashProfile {
        Objects.requireNonNull(prime, "prime");
        Objects.requireNonNull(base, "base");
    }

    static void require(Node node, PolynomialHash h) {
        if (node != null && !node.profile().equals(h.profile())) {
            throw new IllegalArgumentException("Incompatible hash profiles; rebuild bytes with one profile");
        }
    }
}
