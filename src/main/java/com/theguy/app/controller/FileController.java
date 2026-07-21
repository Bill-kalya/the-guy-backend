package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${api.url:https://api.theguy.co.ke}")
    private String apiUrl;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedType(contentType)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File type not allowed. Use JPG, PNG, or PDF."));
        }

        // Validate file size (5MB max)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File size must be under 5MB"));
        }

        try {
            String filename = UUID.randomUUID() + getExtension(file.getOriginalFilename());
            Path dirPath = Paths.get(uploadDir, folder);
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(filename);
            file.transferTo(filePath.toFile());

            String url = apiUrl + "/api/files/" + folder + "/" + filename;
            log.info("File uploaded: {}", url);

            return ResponseEntity.ok(ApiResponse.success("File uploaded", url));
        } catch (IOException e) {
            log.error("File upload failed", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Upload failed"));
        }
    }

    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<?> getFile(@PathVariable String folder, @PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadDir, folder, filename);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isAllowedType(String contentType) {
        return contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp")
                || contentType.equals("application/pdf");
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }
}
