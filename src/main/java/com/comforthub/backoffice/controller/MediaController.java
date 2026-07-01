package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.ImageGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Media endpoints — Bubble file upload and AI image generation.
 *
 * <p><b>#115 hardening:</b> both endpoints require a resolvable company context
 * (403 otherwise — they proxy shared Bubble/Gemini resources); {@code /upload}
 * accepts only common image content types and at most {@value #MAX_UPLOAD_BYTES}
 * bytes (400 otherwise); {@code /generate} additionally requires the OWNER role
 * (it spends paid Gemini quota).
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    /** Upload cap — images only, so 5 MB is plenty. */
    static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    /** Content types accepted by {@code /upload}. */
    static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final BubbleClient bubbleClient;
    private final ImageGenerationService imageGenerationService;
    private final CurrentUserService currentUserService;

    public MediaController(BubbleClient bubbleClient,
                           ImageGenerationService imageGenerationService,
                           CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.imageGenerationService = imageGenerationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (currentUserService.currentCompanyId().isEmpty()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No company context for the current user"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String contentType = file.getContentType() == null
                ? null
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported content type — allowed: image/jpeg, image/png, image/webp, image/gif"));
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File exceeds the 5 MB upload limit"));
        }

        try {
            log.info("Receiving file upload request: name={}, size={}", file.getOriginalFilename(), file.getSize());
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            String url = bubbleClient.uploadFile(filename, file.getBytes());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody Map<String, String> request) {
        // OWNER-only (paid Gemini quota); 403 via GlobalExceptionHandler for non-owners.
        currentUserService.requireOwner();
        if (currentUserService.currentCompanyId().isEmpty()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No company context for the current user"));
        }

        String prompt = request.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }

        try {
            log.info("Receiving image generation request with prompt: {}", prompt);
            String url = imageGenerationService.generateImage(prompt);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("Failed to generate image", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
