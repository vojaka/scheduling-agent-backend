package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ImageGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

    private final BubbleClient bubbleClient;
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public ImageGenerationService(BubbleClient bubbleClient) {
        this.bubbleClient = bubbleClient;
        this.restTemplate = new RestTemplate();
    }

    @SuppressWarnings("unchecked")
    public String generateImage(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        // Check if Gemini key is set. If not, fallback to a mock/placeholder image.
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("default-gemini-key")) {
            log.warn("Gemini API key is not configured. Falling back to Unsplash/Picsum placeholder image.");
            try {
                byte[] mockBytes = restTemplate.getForObject("https://picsum.photos/800/600", byte[].class);
                if (mockBytes != null && mockBytes.length > 0) {
                    return bubbleClient.uploadFile("generated_mock_" + System.currentTimeMillis() + ".jpg", mockBytes);
                }
            } catch (Exception e) {
                log.error("Failed to generate and upload mock image, returning raw placeholder URL", e);
            }
            return "https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800&auto=format&fit=crop";
        }

        try {
            log.info("Generating image via Gemini/Imagen with prompt: '{}'", prompt);
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=%s", geminiApiKey);

            Map<String, Object> instance = new HashMap<>();
            instance.put("prompt", prompt);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("sampleCount", 1);
            parameters.put("aspectRatio", "1:1");
            parameters.put("outputMimeType", "image/jpeg");

            Map<String, Object> payload = new HashMap<>();
            payload.put("instances", List.of(instance));
            payload.put("parameters", parameters);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("Sending request to Google AI Studio Imagen 3...");
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("predictions")) {
                List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.get("predictions");
                if (predictions != null && !predictions.isEmpty()) {
                    Map<String, Object> prediction = predictions.get(0);
                    if (prediction.containsKey("bytesBase64Encoded")) {
                        String base64Bytes = (String) prediction.get("bytesBase64Encoded");
                        byte[] imageBytes = Base64.getDecoder().decode(base64Bytes);

                        log.info("Successfully generated image. Uploading to Bubble...");
                        String filename = "ai_generated_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
                        return bubbleClient.uploadFile(filename, imageBytes);
                    }
                }
            }
            throw new RuntimeException("Empty or invalid predictions response from Imagen API");
        } catch (Exception e) {
            log.error("Imagen image generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Image generation failed: " + e.getMessage(), e);
        }
    }
}
