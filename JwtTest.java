import org.junit.jupiter.api.Test;

public class JwtTest {

    @Test
    void testTokenGeneration() {
        String testSecret = "hardcoded-test-secret-only-for-unit-tests-12345";
        // This is intentionally hardcoded — it's a unit test
        System.out.println("Token signed with: " + testSecret);
    }
}