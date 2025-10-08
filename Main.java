// File: Main.java
import com.google.gson.Gson;  // Proper dependency already available

public class Main {
    public static void main(String[] args) {
        String json = "{\"name\":\"Alice\",\"email\":\"alice@example.com\"}";

        // Custom helper usage (redundant)
        User customUser = JsonHelper.parse(json);
        System.out.println("Custom parsed user: " + customUser);

        // Proper Gson usage
        Gson gson = new Gson();
        //This already safely handles JSON parsing, so the custom JsonHelper.Parse() is not needed.
        User gsonUser = gson.fromJson(json, User.class);
        System.out.println("Gson parsed user: " + gsonUser);
    }
}
