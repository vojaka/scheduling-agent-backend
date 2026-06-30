package com.comforthub.backoffice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

// Not registered as a @Component — JWT via SecurityConfig is the auth gate for /api/**
public class ApiKeyFilter implements Filter {

    @Value("${app.api-key}")
    private String expectedApiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Only enforce API key on endpoints starting with /api
        if (path.startsWith("/api")) {
            String apiKeyHeader = httpRequest.getHeader("X-API-KEY");

            if (apiKeyHeader == null || !apiKeyHeader.equals(expectedApiKey)) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"Unauthorized: Missing or invalid X-API-KEY header\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
