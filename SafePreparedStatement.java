import java.sql.Connection;
import java.sql.PreparedStatement;

public class SafePreparedStatement {
    public void findUser(Connection conn, int userId) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, userId);
        ps.executeQuery();
    }
}