public class JwtConfig {
    // Hardcoded secret — critical finding
    public static final String SECRET_KEY = "S3cReTjWtK3y_2025_Hardc0ded_f0r_Dem0_PurP0se_1234567890";

    public static String getSecret() {
        return SECRET_KEY;
    }
}