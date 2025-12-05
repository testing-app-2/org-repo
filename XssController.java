import org.springframework.web.bind.annotation.*;

@RestController
public class XssController {

    @GetMapping("/search")
    public String search(@RequestParam String q) {
        return "<h1>Search results for: " + q + "</h1>" +
               "<p>You searched for: " + q + "</p>";
    }
}