// File: JsonHelper.java
public class JsonHelper {
    public static User parse(String json) {
        // Extremely naive parser: assumes simple {"name":"..","email":".."}
        String name = json.substring(json.indexOf("name") + 7, json.indexOf("email") - 3);
        String email = json.substring(json.indexOf("email") + 8, json.length() - 2);
        return new User(name, email);
    }
}
