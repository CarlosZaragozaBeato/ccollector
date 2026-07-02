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
@Table(name = "athlete_profiles")
public class AthleteProfile extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Functional threshold power in watts, promoted from the Strava athlete
     * sync ({@code /api/v3/athlete}). Null for athletes with no power data —
     * absence of a power meter is expected, not an error.
     */
    @Column(name = "ftp_watts")
    private Integer ftpWatts;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getFtpWatts() {
        return ftpWatts;
    }

    public void setFtpWatts(Integer ftpWatts) {
        this.ftpWatts = ftpWatts;
    }
}
