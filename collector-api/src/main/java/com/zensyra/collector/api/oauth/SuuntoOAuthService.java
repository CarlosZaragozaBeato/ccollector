package com.zensyra.collector.api.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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
public class SuuntoOAuthService {

    private static final Logger LOG = Logger.getLogger(SuuntoOAuthService.class);

    @ConfigProperty(name = "suunto.oauth.token-url",
            defaultValue = "https://cloudapi-oauth.suunto.com/oauth/token")
    String tokenUrl;

    @Inject
    IntegrationCredentialRepository credentialRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * No scope parameter, unlike Strava: Suunto's authorize URL has no scope
     * mechanism and the granted scope arrives in the token response body itself.
     */
    public SuuntoOAuthToken exchangeAuthorizationCode(String code, String redirectUri) {
        IntegrationCredential credential = credentialRepository
                .findBySource(IntegrationSource.SUUNTO)
                .orElseThrow(() -> new SuuntoOAuthExchangeException(
                        "No Suunto credentials were found in the database"
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
            throw new SuuntoOAuthExchangeException("Could not connect to Suunto OAuth", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuuntoOAuthExchangeException("Interrupted while exchanging the OAuth code with Suunto", e);
        }

        if (response.statusCode() != 200) {
            // Log only the status code — never the raw external response body, which
            // is unnecessary exposure of an upstream payload we don't control (same
            // approach as #39/#42 in the Strava OAuth path). The exception message
            // is reflected to the API caller by SuuntoAthleteRegisterResource, so it
            // must stay body-free too.
            LOG.errorf("Suunto OAuth exchange failed with status %d", response.statusCode());
            throw new SuuntoOAuthExchangeException(
                    "Suunto OAuth exchange failed — HTTP %d".formatted(response.statusCode())
            );
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode userNode = json.get("user");
            if (userNode == null || userNode.isNull() || userNode.asText().isBlank()) {
                throw new SuuntoOAuthExchangeException("Suunto OAuth response missing user");
            }

            // Suunto returns a relative lifetime (expires_in seconds), not Strava's
            // absolute expires_at epoch. The response also carries uk/ukv/jti fields;
            // they are confirmed redundant and are deliberately never read or stored.
            return new SuuntoOAuthToken(
                    userNode.asText(),
                    json.get("access_token").asText(),
                    json.get("refresh_token").asText(),
                    Instant.now().plusSeconds(json.get("expires_in").asLong()),
                    json.hasNonNull("scope") ? json.get("scope").asText() : null
            );
        } catch (IOException e) {
            throw new SuuntoOAuthExchangeException("Could not parse the Suunto OAuth response", e);
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
