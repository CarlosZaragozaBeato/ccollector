package com.zensyra.collector.api.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class StravaOAuthService {

    @ConfigProperty(name = "strava.oauth.token-url",
            defaultValue = "https://www.strava.com/oauth/token")
    String tokenUrl;

    @Inject
    IntegrationCredentialRepository credentialRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StravaOAuthToken exchangeAuthorizationCode(String code, String redirectUri) {
        IntegrationCredential credential = credentialRepository
                .findBySource(IntegrationSource.STRAVA)
                .orElseThrow(() -> new StravaOAuthExchangeException(
                        "No se encontraron credenciales de Strava en BD"
                ));

        String body = buildFormBody(
                credential.getClientId(),
                credential.getClientSecret(),
                code,
                redirectUri
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new StravaOAuthExchangeException("No se pudo conectar con Strava OAuth", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StravaOAuthExchangeException("Interrumpido al intercambiar código OAuth con Strava", e);
        }

        if (response.statusCode() != 200) {
            throw new StravaOAuthExchangeException(
                    "Strava OAuth exchange failed — HTTP %d: %s"
                            .formatted(response.statusCode(), response.body())
            );
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode athlete = json.get("athlete");
            JsonNode athleteIdNode = athlete != null ? athlete.get("id") : null;
            if (athleteIdNode == null || athleteIdNode.isNull()) {
                throw new StravaOAuthExchangeException("Strava OAuth response missing athlete.id");
            }

            return new StravaOAuthToken(
                    athleteIdNode.asText(),
                    json.get("access_token").asText(),
                    json.get("refresh_token").asText(),
                    Instant.ofEpochSecond(json.get("expires_at").asLong())
            );
        } catch (IOException e) {
            throw new StravaOAuthExchangeException("No se pudo parsear la respuesta OAuth de Strava", e);
        }
    }

    private String buildFormBody(String clientId, String clientSecret, String code, String redirectUri) {
        List<String> parts = new ArrayList<>();
        parts.add("client_id=" + encode(clientId));
        parts.add("client_secret=" + encode(clientSecret));
        parts.add("code=" + encode(code));
        parts.add("grant_type=authorization_code");
        if (redirectUri != null && !redirectUri.isBlank()) {
            parts.add("redirect_uri=" + encode(redirectUri));
        }
        return String.join("&", parts);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
