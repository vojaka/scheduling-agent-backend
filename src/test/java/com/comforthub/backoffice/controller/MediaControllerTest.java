package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.ImageGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import(SecurityConfig.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ImageGenerationService imageGenerationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void upload_returnsFileUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test-image-content".getBytes()
        );

        when(bubbleClient.uploadFile(eq("test.jpg"), any())).thenReturn("https://bubble.s3.amazonaws.com/test.jpg");

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://bubble.s3.amazonaws.com/test.jpg"));
    }

    @Test
    void generate_returnsGeneratedFileUrl() throws Exception {
        String prompt = "A cute puppy playing in the grass";
        when(imageGenerationService.generateImage(prompt)).thenReturn("https://bubble.s3.amazonaws.com/generated.jpg");

        mockMvc.perform(post("/api/media/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"" + prompt + "\"}")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://bubble.s3.amazonaws.com/generated.jpg"));
    }

    @Test
    void generate_withEmptyPrompt_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/media/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prompt is required"));
    }
}
