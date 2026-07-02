package com.zensyra.collector.journal.model;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "training_days")
public class TrainingDay extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "perceived_effort")
    private Integer perceivedEffort;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjective_state", length = 16)
    private SubjectiveState subjectiveState;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public void setAthleteId(UUID athleteId) {
        this.athleteId = athleteId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getPerceivedEffort() {
        return perceivedEffort;
    }

    public void setPerceivedEffort(Integer perceivedEffort) {
        this.perceivedEffort = perceivedEffort;
    }

    public SubjectiveState getSubjectiveState() {
        return subjectiveState;
    }

    public void setSubjectiveState(SubjectiveState subjectiveState) {
        this.subjectiveState = subjectiveState;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
