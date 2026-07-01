package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MediaController contract incl. the #115 hardening: company context required
 * (403), image-only content types + 5 MB cap on /upload (400), and the
 * OWNER-only gate on /generate.
 */
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
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private void givenCompanyContext() {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
    }

    // ----------------------------------------------------------------- upload

    @Test
    void upload_returnsFileUrl() throws Exception {
        givenCompanyContext();
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
    void upload_withoutCompanyContext_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "x".getBytes());

        mockMvc.perform(multipart("/api/media/upload").file(file).with(jwt()))
                .andExpect(status().isForbidden());

        verify(bubbleClient, never()).uploadFile(any(), any());
    }

    @Test
    void upload_nonImageContentType_isBadRequest() throws Exception {
        givenCompanyContext();
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.html", MediaType.TEXT_HTML_VALUE, "<script>alert(1)</script>".getBytes());

        mockMvc.perform(multipart("/api/media/upload").file(file).with(jwt()))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).uploadFile(any(), any());
    }

    @Test
    void upload_oversizedFile_isBadRequest() throws Exception {
        givenCompanyContext();
        byte[] oversized = new byte[(int) (MediaController.MAX_UPLOAD_BYTES + 1)];
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.png", MediaType.IMAGE_PNG_VALUE, oversized);

        mockMvc.perform(multipart("/api/media/upload").file(file).with(jwt()))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).uploadFile(any(), any());
    }

    // --------------------------------------------------------------- generate

    @Test
    void generate_returnsGeneratedFileUrl() throws Exception {
        givenCompanyContext();
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
        givenCompanyContext();
        mockMvc.perform(post("/api/media/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prompt is required"));
    }

    @Test
    void generate_asNonOwner_isForbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/media/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"anything\"}")
                        .with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(imageGenerationService, never()).generateImage(any());
    }

    @Test
    void generate_withoutCompanyContext_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/media/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"anything\"}")
                        .with(jwt()))
                .andExpect(status().isForbidden());

        verify(imageGenerationService, never()).generateImage(any());
    }
}
