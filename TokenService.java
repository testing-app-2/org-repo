import java.util.Random;

public class TokenService {
    private static final Random random = new Random();

    public String generateResetToken() {
        // Weak & predictable — should be flagged
        return String.valueOf(random.nextLong());
    }

    public String generateSessionId() {
        return String.valueOf(Math.random() * 1_000_000);
    }
}