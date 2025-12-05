public class SafeCommandExecutor {

    public void manageService(String action) throws Exception {
        String command = switch (action) {
            case "start"  -> "/usr/bin/systemctl start nginx";
            case "stop"   -> "/usr/bin/systemctl stop nginx";
            case "restart"-> "/usr/bin/systemctl restart nginx";
            default       -> throw new IllegalArgumentException("Invalid action: " + action);
        };
        Runtime.getRuntime().exec(command);
    }
}