package com.theguy.app.controller;

import com.theguy.app.dto.ApiResponse;
import com.theguy.app.entity.PortfolioImage;
import com.theguy.app.entity.VerificationDocument;
import com.theguy.app.repository.PortfolioImageRepository;
import com.theguy.app.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final PortfolioImageRepository portfolioImageRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    private static final Set<String> ALLOWED_FOLDERS = Set.of(
        "profile", "portfolio", "documents", "avatars", "general"
    );

    private static final long MAX_SIZE_PROFILE = 5 * 1024 * 1024;
    private static final long MAX_SIZE_PORTFOLIO = 10 * 1024 * 1024;
    private static final long MAX_SIZE_DOCUMENTS = 10 * 1024 * 1024;
    private static final long MAX_SIZE_DEFAULT = 5 * 1024 * 1024;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }

        if (!ALLOWED_FOLDERS.contains(folder)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid folder: " + folder));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File type not allowed. Use JPG, PNG, or WebP."));
        }

        long maxSize = switch (folder) {
            case "profile", "avatars" -> MAX_SIZE_PROFILE;
            case "portfolio" -> MAX_SIZE_PORTFOLIO;
            case "documents" -> MAX_SIZE_DOCUMENTS;
            default -> MAX_SIZE_DEFAULT;
        };
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                "File too large. Max " + (maxSize / 1024 / 1024) + "MB for " + folder));
        }

        if (cloudName == null || cloudName.isBlank()) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Cloudinary not configured"));
        }

        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String publicId = folder + "/" + UUID.randomUUID().toString().substring(0, 8);

            String toSign = "folder=theguy/" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
            String signature = DigestUtils.md5DigestAsHex(toSign.getBytes());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("folder", "theguy/" + folder);
            body.add("public_id", publicId);
            body.add("signature", signature);
            body.add("resource_type", "image");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String url = (String) response.getBody().get("secure_url");
                log.info("Uploaded to Cloudinary: {} → {} (public_id: {})", file.getOriginalFilename(), url, publicId);

                return ResponseEntity.ok(ApiResponse.success("File uploaded", Map.of(
                    "url", url,
                    "publicId", publicId
                )));
            } else {
                log.error("Cloudinary upload failed: {}", response.getBody());
                return ResponseEntity.internalServerError().body(ApiResponse.error("Upload failed"));
            }
        } catch (Exception e) {
            log.error("Cloudinary upload failed", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestParam String publicId) {

        if (cloudName == null || cloudName.isBlank()) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Cloudinary not configured"));
        }

        // Ownership validation: verify the publicId belongs to this user
        String principal = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal != null) {
            try {
                UUID userId = UUID.fromString(principal);
                boolean owns = verifyOwnership(publicId, userId);
                if (!owns) {
                    log.warn("User {} attempted to delete file they don't own: {}", userId, publicId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You don't have permission to delete this file"));
                }
            } catch (Exception e) {
                log.warn("Could not verify ownership for principal: {}", principal);
            }
        }

        // Soft-delete in database if it's a portfolio or verification image
        softDeleteInDatabase(publicId);

        // Delete from Cloudinary
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String toSign = "public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
            String signature = DigestUtils.md5DigestAsHex(toSign.getBytes());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("public_id", publicId);
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("signature", signature);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            String deleteUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy";
            ResponseEntity<Map> response = restTemplate.postForEntity(deleteUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Deleted from Cloudinary: {}", publicId);
                return ResponseEntity.ok(ApiResponse.success("File deleted", null));
            } else {
                return ResponseEntity.internalServerError().body(ApiResponse.error("Delete failed"));
            }
        } catch (Exception e) {
            log.error("Cloudinary delete failed", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Delete failed: " + e.getMessage()));
        }
    }

    private boolean verifyOwnership(String publicId, UUID userId) {
        // Check portfolio images
        if (portfolioImageRepository.findByPublicIdAndUserId(publicId, userId).isPresent()) {
            return true;
        }
        // Check verification documents
        if (verificationDocumentRepository.findByPublicIdAndUserId(publicId, userId).isPresent()) {
            return true;
        }
        return false;
    }

    private void softDeleteInDatabase(String publicId) {
        // Soft-delete portfolio image
        portfolioImageRepository.findByPublicId(publicId).ifPresent(img -> {
            img.setIsActive(false);
            portfolioImageRepository.save(img);
            log.info("Soft-deleted portfolio image: {}", publicId);
        });

        // Soft-delete verification document
        verificationDocumentRepository.findByPublicId(publicId).ifPresent(doc -> {
            doc.setStatus(com.theguy.app.entity.VerificationDocument.VerificationDocumentStatus.DELETED);
            verificationDocumentRepository.save(doc);
            log.info("Soft-deleted verification document: {}", publicId);
        });
    }
}
