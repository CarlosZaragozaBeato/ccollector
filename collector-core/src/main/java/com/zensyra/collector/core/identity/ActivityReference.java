package com.zensyra.collector.core.identity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_references")
public class ActivityReference extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "training_session_id", nullable = false)
    private UUID trainingSessionId;

    @Column(name = "integration_account_id", nullable = false)
    private UUID integrationAccountId;

    @Column(name = "external_activity_id", nullable = false, length = 255)
    private String externalActivityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivityReference() {
    }

    public ActivityReference(
            UUID athleteId,
            UUID trainingSessionId,
            UUID integrationAccountId,
            String externalActivityId) {
        this.athleteId = athleteId;
        this.trainingSessionId = trainingSessionId;
        this.integrationAccountId = integrationAccountId;
        this.externalActivityId = externalActivityId;
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

    public UUID getTrainingSessionId() {
        return trainingSessionId;
    }

    public UUID getIntegrationAccountId() {
        return integrationAccountId;
    }

    public String getExternalActivityId() {
        return externalActivityId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
