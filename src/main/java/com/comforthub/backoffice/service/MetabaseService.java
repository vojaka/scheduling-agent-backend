package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class MetabaseService {

    private static final Logger log = LoggerFactory.getLogger(MetabaseService.class);

    private final RestClient restClient;
    private final String metabaseSiteUrl;
    private final String metabaseEmbedSecret;
    private final String metabaseAdminEmail;
    private final String metabaseAdminPassword;

    private String cachedSessionToken = null;

    public MetabaseService(
            @Value("${metabase.site.url:http://178.105.76.235:3000}") String metabaseSiteUrl,
            @Value("${metabase.embed.secret:}") String metabaseEmbedSecret,
            @Value("${metabase.admin.email:kim.smirnov@gmail.com}") String metabaseAdminEmail,
            @Value("${metabase.admin.password:}") String metabaseAdminPassword) {
        this.metabaseSiteUrl = metabaseSiteUrl;
        this.metabaseEmbedSecret = metabaseEmbedSecret;
        this.metabaseAdminEmail = metabaseAdminEmail;
        this.metabaseAdminPassword = metabaseAdminPassword;
        this.restClient = RestClient.builder().baseUrl(metabaseSiteUrl).build();
    }

    private synchronized String getSessionToken(boolean forceRefresh) {
        if (!forceRefresh && cachedSessionToken != null) {
            return cachedSessionToken;
        }

        try {
            log.info("Authenticating with Metabase at {}...", metabaseSiteUrl);
            Map<String, String> loginPayload = new HashMap<>();
            loginPayload.put("username", metabaseAdminEmail);
            loginPayload.put("password", metabaseAdminPassword);

            Map<String, Object> response = restClient.post()
                    .uri("/api/session")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(loginPayload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("id")) {
                cachedSessionToken = (String) response.get("id");
                log.info("Successfully authenticated with Metabase.");
                return cachedSessionToken;
            }
        } catch (Exception e) {
            log.error("Failed to authenticate with Metabase", e);
        }
        return null;
    }

    public List<ReportDto> getReports(String company) {
        try {
            return fetchReports(company, false);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Metabase session unauthorized. Attempting to re-authenticate...");
            try {
                return fetchReports(company, true);
            } catch (Exception ex) {
                log.error("Failed to fetch reports after re-authentication", ex);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to fetch reports from Metabase", e);
            return Collections.emptyList();
        }
    }

    private List<ReportDto> fetchReports(String company, boolean forceRefreshSession) {
        String sessionToken = getSessionToken(forceRefreshSession);
        if (sessionToken == null) {
            log.warn("Cannot fetch Metabase reports: Session token is null");
            return Collections.emptyList();
        }

        List<ReportDto> reports = new ArrayList<>();

        // 1. Fetch Dashboards
        try {
            List<Map<String, Object>> dashboards = restClient.get()
                    .uri("/api/dashboard")
                    .header("X-Metabase-Session", sessionToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (dashboards != null) {
                for (Map<String, Object> dash : dashboards) {
                    String desc = (String) dash.get("description");
                    if (isVisibleInBackoffice(desc)) {
                        Integer id = (Integer) dash.get("id");
                        String name = (String) dash.get("name");
                        String embedUrl = generateEmbedUrl("dashboard", id, company);
                        reports.add(new ReportDto("dashboard-" + id, name, cleanDescription(desc), "dashboard", embedUrl));
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException.Unauthorized) {
                throw (HttpClientErrorException.Unauthorized) e;
            }
            log.error("Failed to fetch dashboards from Metabase", e);
        }

        // 2. Fetch Cards (Questions)
        try {
            List<Map<String, Object>> cards = restClient.get()
                    .uri("/api/card")
                    .header("X-Metabase-Session", sessionToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (cards != null) {
                for (Map<String, Object> card : cards) {
                    String desc = (String) card.get("description");
                    if (isVisibleInBackoffice(desc)) {
                        Integer id = (Integer) card.get("id");
                        String name = (String) card.get("name");
                        String embedUrl = generateEmbedUrl("question", id, company);
                        reports.add(new ReportDto("card-" + id, name, cleanDescription(desc), "question", embedUrl));
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException.Unauthorized) {
                throw (HttpClientErrorException.Unauthorized) e;
            }
            log.error("Failed to fetch cards from Metabase", e);
        }

        return reports;
    }

    private boolean isVisibleInBackoffice(String description) {
        return description != null && description.toLowerCase().contains("#backoffice");
    }

    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.replaceAll("(?i)#backoffice", "").trim();
    }

    private String generateEmbedUrl(String type, int id, String company) {
        if (metabaseEmbedSecret == null || metabaseEmbedSecret.trim().isEmpty()) {
            log.warn("METABASE_EMBED_SECRET is not configured. Cannot generate embed URL.");
            return null;
        }

        try {
            com.auth0.jwt.algorithms.Algorithm algorithm =
                    com.auth0.jwt.algorithms.Algorithm.HMAC256(metabaseEmbedSecret);

            Map<String, Object> resource = new HashMap<>();
            if ("dashboard".equalsIgnoreCase(type)) {
                resource.put("dashboard", id);
            } else {
                resource.put("question", id);
            }

            Map<String, Object> params = new HashMap<>();
            if (company != null && !company.trim().isEmpty()) {
                params.put("company", company);
            }

            long expSeconds = (System.currentTimeMillis() / 1000L) + 600L;

            String token = com.auth0.jwt.JWT.create()
                    .withClaim("resource", resource)
                    .withClaim("params", params)
                    .withClaim("exp", expSeconds)
                    .sign(algorithm);

            String pathType = "dashboard".equalsIgnoreCase(type) ? "dashboard" : "question";
            return String.format("%s/embed/%s/%s#theme=light&bordered=false&titled=true",
                    metabaseSiteUrl, pathType, token);
        } catch (Exception e) {
            log.error("Failed to generate Metabase embed URL for {} ID {}", type, id, e);
            return null;
        }
    }
}
