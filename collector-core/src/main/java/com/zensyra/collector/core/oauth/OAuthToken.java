package com.zensyra.collector.core.oauth;

import com.zensyra.collector.core.crypto.AesGcmAttributeConverter;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_tokens")
public class OAuthToken extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationSource source;

    @Column(name="external_user_id",nullable = false)
    private String externalUserId;

    @Column(name = "integration_account_id")
    private UUID integrationAccountId;

    // No length cap: Suunto tokens are JWTs (700+ chars) and the AES-GCM +
    // base64 encrypted form grows further — the columns are TEXT (migration 040).
    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name="expires_at",nullable = false)
    private Instant expiresAt;

    // OAuth scope granted for this token (e.g. "read,activity:read_all"). Nullable:
    // Strava delivers scope on the authorization redirect, not the token response,
    // so it is only populated when the client forwards it on register. Not a secret,
    // hence not encrypted — kept for diagnostic visibility into insufficient grants.
    @Column(name = "scope")
    private String scope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        setCreatedAt(Instant.now());
        setUpdatedAt(getCreatedAt());
    }

    @PreUpdate
    void onUpdate() {
        setUpdatedAt(Instant.now());
    }

    public boolean isExpired(){
        // The 60s margin is a safety buffer BEFORE expiry: a token that expires
        // within the next minute is already treated as expired, so callers never
        // receive one that lapses mid-request. Source-agnostic — works the same
        // for Strava's 6h window and Suunto's 24h window.
        return expiresAt.isBefore(Instant.now().plusSeconds(60));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IntegrationSource getSource() {
        return source;
    }

    public void setSource(IntegrationSource source) {
        this.source = source;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public UUID getIntegrationAccountId() {
        return integrationAccountId;
    }

    public void setIntegrationAccountId(UUID integrationAccountId) {
        this.integrationAccountId = integrationAccountId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
