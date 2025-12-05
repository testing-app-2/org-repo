import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SystemTool {

    public String executePing(String ip) throws Exception {
        // Direct user input into shell command → OS Command Injection
        String command = "ping -c 4 " + ip;
        Process process = Runtime.getRuntime().exec(command);

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }
}