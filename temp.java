import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.sql.*;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class SecureService {
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>();
    static {
        ALLOWED_COMMANDS.add("uptime");
        ALLOWED_COMMANDS.add("whoami");
    }

    private final Path safeBaseDir = Paths.get("/var/app/data").toAbsolutePath().normalize();
    private final Connection dbConnection;

    public SecureService(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public boolean isAuthenticated(String authToken) {
        if (authToken == null || authToken.length() < 10) return false;
        String sql = "SELECT user_id FROM auth_tokens WHERE token = ? AND expires_at > CURRENT_TIMESTAMP";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, authToken);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public String getUserDisplayName(int userId, String authToken) throws SecurityException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        String sql = "SELECT display_name FROM users WHERE id = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("display_name");
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB error", e);
        }
    }

    public byte[] readUserFile(String requestedPath, String authToken) throws SecurityException, IOException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        Path resolved = safeBaseDir.resolve(requestedPath).normalize();
        if (!resolved.startsWith(safeBaseDir)) {
            throw new SecurityException("Forbidden path");
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new FileNotFoundException("File not found");
        }
        long size = Files.size(resolved);
        if (size > 10 * 1024 * 1024) throw new IOException("File too large");
        return Files.readAllBytes(resolved);
    }

    public String runAllowedCommand(String command, String[] args, String authToken) throws SecurityException, IOException, InterruptedException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        if (!ALLOWED_COMMANDS.contains(command)) {
            throw new SecurityException("Command not allowed");
        }
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(command);
        if (args != null) {
            for (String a : args) {
                if (a == null || a.length() > 200) throw new SecurityException("Invalid arg");
                if (a.matches(".*[;|&<>`\\\\].*")) throw new SecurityException("Invalid characters in arg");
                pb.command().add(a);
            }
        }
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 50000) {
                    proc.destroyForcibly();
                    throw new IOException("Command output too large");
                }
            }
            int exit = proc.waitFor();
            return "Exit=" + exit + "\n" + sb.toString();
        }
    }

    public Object safeDeserialize(byte[] payload, String authToken) throws SecurityException, IOException, ClassNotFoundException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        try (ObjectInputStream ois = new WhitelistObjectInputStream(new ByteArrayInputStream(payload))) {
            return ois.readObject();
        }
    }

    private static class WhitelistObjectInputStream extends ObjectInputStream {
        private static final Set<String> ALLOWED = new HashSet<>();
        static {
            ALLOWED.add("java.util.ArrayList");
            ALLOWED.add("java.lang.String");
        }
        WhitelistObjectInputStream(InputStream in) throws IOException {
            super(in);
        }
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            if (!ALLOWED.contains(name)) {
                throw new InvalidClassException("Unauthorized deserialization", name);
            }
            return super.resolveClass(desc);
        }
    }

    public void useResourceSafely(String authToken) throws SecurityException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        try (InputStream in = openSomeStream()) {
            byte[] buf = new byte[1024];
            int read = in.read(buf);
        } catch (IOException e) {
        }
    }

    private InputStream openSomeStream() throws FileNotFoundException {
        return new ByteArrayInputStream("hello".getBytes());
    }

    public int safeArrayAccess(int[] arr, int index, String authToken) throws SecurityException {
        if (!isAuthenticated(authToken)) throw new SecurityException("Not authenticated");
        if (arr == null) throw new IllegalArgumentException("arr null");
        if (index < 0 || index >= arr.length) throw new IndexOutOfBoundsException("Index invalid");
        return arr[index];
    }

    public boolean isLocalOrAllowedHost(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) return false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
