package com.example.secure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api")
@Validated
public class SecureController {

    private static final Path BASE_DIR = Paths.get("/var/app/allowed_files").toAbsolutePath().normalize();
    private final JdbcTemplate jdbc;

    @Autowired
    public SecureController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/file/{filename}")
    public ResponseEntity<byte[]> getFile(
            @PathVariable
            @Pattern(regexp = "^[A-Za-z0-9_.\\-]{1,100}$", message = "Invalid filename") String filename
    ) throws IOException {
        Path requested = BASE_DIR.resolve(filename).normalize();
        if (!requested.startsWith(BASE_DIR)) {
            return ResponseEntity.status(403).body(("Forbidden").getBytes());
        }
        if (!Files.exists(requested) || !Files.isRegularFile(requested)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = Files.readAllBytes(requested);
        String contentType = Files.probeContentType(requested);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<String> getUserDisplayName(
            @PathVariable
            @Pattern(regexp = "^[0-9]{1,10}$", message = "Invalid id") String idStr
    ) {
        int id = Integer.parseInt(idStr);
        String sql = "SELECT display_name FROM users WHERE id = ?";
        String displayName;
        try {
            displayName = jdbc.queryForObject(sql, new Object[]{id}, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("User not found");
        }
        return ResponseEntity.ok(displayName);
    }
}
