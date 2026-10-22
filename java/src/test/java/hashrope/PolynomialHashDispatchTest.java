package hashrope;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies that subclass dispatch follows the pinned Python primitive call graph. */
class PolynomialHashDispatchTest {
    private static class NoCompositeDispatchHash extends PolynomialHash {
        @Override public BigInteger hashRepeat(BigInteger hash, BigInteger length, BigInteger repetitions) {
            throw new AssertionError("hashOverlap must not call overridden hashRepeat");
        }

        @Override public BigInteger hashConcat(BigInteger hashA, BigInteger lengthB, BigInteger hashB) {
            throw new AssertionError("hashOverlap must not call overridden hashConcat");
        }
    }

    private static final class PowerTracingHash extends NoCompositeDispatchHash {
        final List<BigInteger> exponents = new ArrayList<>();

        @Override public BigInteger power(BigInteger exponent) {
            exponents.add(exponent);
            return exponent.equals(BigInteger.valueOf(3)) ? BigInteger.valueOf(5) : BigInteger.valueOf(7);
        }
    }

    @Test void overlapBypassesCompositeOverridesAndHonorsPowerOverrides() {
        PowerTracingHash h = new PowerTracingHash();
        assertEquals(BigInteger.valueOf(1964), h.hashOverlap(
                BigInteger.valueOf(9), BigInteger.valueOf(3), BigInteger.TEN, BigInteger.valueOf(11)));
        assertEquals(List.of(BigInteger.valueOf(3), BigInteger.ONE), h.exponents);
    }

    @Test void longOverloadRetainsTheSamePrimitiveDispatch() {
        PowerTracingHash h = new PowerTracingHash();
        assertEquals(BigInteger.valueOf(1964), h.hashOverlap(BigInteger.valueOf(9), 3, 10, BigInteger.valueOf(11)));
        assertEquals(List.of(BigInteger.valueOf(3), BigInteger.ONE), h.exponents);
    }

    @Test void zeroDivisorFailsBeforeCallingPower() {
        PowerTracingHash h = new PowerTracingHash();
        assertEquals("integer division or modulo by zero", assertThrows(ArithmeticException.class,
                () -> h.hashOverlap(BigInteger.ONE, BigInteger.ZERO, BigInteger.TEN, BigInteger.ZERO)).getMessage());
        assertTrue(h.exponents.isEmpty());
    }
}
