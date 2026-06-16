package com.zensyra.collector.strava.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.oauth.TokenRefreshException;
import com.zensyra.collector.core.oauth.TokenRefreshResult;
import com.zensyra.collector.core.oauth.TokenRefresher;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@ApplicationScoped
public class StravaTokenRefresher implements TokenRefresher {

    private static final Logger LOG = Logger.getLogger(StravaTokenRefresher.class);

    @ConfigProperty(name = "strava.oauth.token-url",
                    defaultValue = "https://www.strava.com/oauth/token")
    String tokenUrl;

    @Inject
    IntegrationCredentialRepository credentialRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IntegrationSource source() {
        return IntegrationSource.STRAVA;
    }

    @Override
    @Retry(maxRetries = 2, delay = 2000, jitter = 500,
           retryOn = {IOException.class, InterruptedException.class})
    public TokenRefreshResult refresh(String refreshToken) throws IOException, InterruptedException {
        IntegrationCredential credential = credentialRepository
                .findBySource(IntegrationSource.STRAVA)
                .orElseThrow(() -> new TokenRefreshException(
                        "No se encontraron credenciales de Strava en BD — " +
                                "inserta una fila en integration_credentials con source='STRAVA'"
                ));

        String body = buildFormBody(
                credential.getClientId(),
                credential.getClientSecret(),
                refreshToken
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // IOException and InterruptedException propagate to the @Retry interceptor,
        // which retries up to 2 times on transient network failures.
        // Non-retriable errors (non-200 status, JSON parse) throw TokenRefreshException directly.
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOG.errorf("Strava token refresh failed — HTTP %d: %s", response.statusCode(), response.body());
            throw new TokenRefreshException(
                    "Strava token refresh failed — HTTP %d".formatted(response.statusCode())
            );
        }

        JsonNode json = objectMapper.readTree(response.body());

        String newAccessToken  = json.get("access_token").asText();
        String newRefreshToken = json.get("refresh_token").asText();
        Instant expiresAt      = Instant.ofEpochSecond(json.get("expires_at").asLong());

        return new TokenRefreshResult(newAccessToken, newRefreshToken, expiresAt);
    }

    private String buildFormBody(String clientId, String clientSecret, String refreshToken) {
        return "client_id="     + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
