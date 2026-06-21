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

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="access_token", nullable = false, length = 512)
    private String accessToken;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name="refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name="expires_at",nullable = false)
    private Instant expiresAt;

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
        Instant margin = Instant.now().minusSeconds(60);
        return expiresAt.isBefore(margin);
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
