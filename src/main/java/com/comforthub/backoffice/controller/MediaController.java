package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.service.ImageGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final BubbleClient bubbleClient;
    private final ImageGenerationService imageGenerationService;

    public MediaController(BubbleClient bubbleClient, ImageGenerationService imageGenerationService) {
        this.bubbleClient = bubbleClient;
        this.imageGenerationService = imageGenerationService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
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
