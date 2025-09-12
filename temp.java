package com.example.insecure;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.io.*;
import java.nio.file.*;

@RestController
@RequestMapping("/insecure")
public class InsecureController {

    private static final Path UPLOAD_DIR = Paths.get("uploads");

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        Path dest = UPLOAD_DIR.resolve(file.getOriginalFilename());
        Files.createDirectories(UPLOAD_DIR);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return ResponseEntity.ok("Uploaded: " + dest.toString());
    }

    @GetMapping("/admin/data")
    public ResponseEntity<String> getAdminData() {
        return ResponseEntity.ok("Sensitive admin data: [TOP SECRET]");
    }

    @GetMapping("/use-after-close")
    public ResponseEntity<String> useAfterClose() throws IOException {
        File file = new File("example.txt");
        FileInputStream fis = new FileInputStream(file);
        fis.close();
        int data = fis.read();
        return new ResponseEntity<>("Read byte: " + data, HttpStatus.OK);
    }

    @GetMapping("/list-uploads")
    public ResponseEntity<String> listUploads() throws IOException {
        StringBuilder sb = new StringBuilder();
        if (Files.exists(UPLOAD_DIR) && Files.isDirectory(UPLOAD_DIR)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(UPLOAD_DIR)) {
                for (Path p : stream) {
                    sb.append(p.getFileName().toString()).append("\n");
                }
            }
        }
        return ResponseEntity.ok(sb.toString());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam("name") String name) throws IOException {
        Path target = UPLOAD_DIR.resolve(name);
        Files.deleteIfExists(target);
        return ResponseEntity.ok("Deleted: " + name);
    }
}
