package hashrope;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiTest {
    @Test void releaseAndBalanceConstantsMatchPinnedPythonModule() {
        assertEquals("0.3.4",Hashrope.VERSION);
        assertEquals(2,Rope.ALPHA_NUM);
        assertEquals(7,Rope.ALPHA_DEN);
    }
    @Test void arithmeticContractRunsUnderMaven() { PolynomialHashTest.runAll(); }
}
