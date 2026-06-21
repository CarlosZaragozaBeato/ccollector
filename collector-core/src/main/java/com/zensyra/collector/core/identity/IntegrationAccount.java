package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_accounts")
public class IntegrationAccount extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IntegrationSource source;

    @Column(name = "external_user_id", nullable = false, length = 255)
    private String externalUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 32)
    private IntegrationConnectionStatus connectionStatus = IntegrationConnectionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationAccount() {
    }

    public IntegrationAccount(UUID athleteId, IntegrationSource source, String externalUserId) {
        this.athleteId = athleteId;
        this.source = source;
        this.externalUserId = externalUserId;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public IntegrationSource getSource() {
        return source;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public IntegrationConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(IntegrationConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
