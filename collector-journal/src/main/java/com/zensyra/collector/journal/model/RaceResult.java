package com.zensyra.collector.journal.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "race_results")
public class RaceResult extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    /**
     * Correlation anchor with {@code athlete_training_load.date} (both DATE /
     * LocalDate) for the PMC join on the date axis.
     *
     * <p>Identity-axis caveat (out of scope for #33, documented for a future
     * correlation issue): {@code athlete_training_load.athlete_id} is a BIGINT
     * (raw Strava numeric id), whereas {@code athleteId} here is the canonical
     * UUID. A full PMC correlation query must bridge identity via
     * {@code integration_accounts}; this entity does not attempt to solve it.
     */
    @Column(name = "race_date", nullable = false)
    private LocalDate raceDate;

    @Column(name = "race_name", nullable = false)
    private String raceName;

    @Column(name = "distance_meters", nullable = false)
    private Double distanceMeters;

    @Column(name = "goal_finish_time")
    private Integer goalFinishTime;

    @Column(name = "actual_finish_time")
    private Integer actualFinishTime;

    @Column(name = "position")
    private Integer position;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Canonical activity UUID for the race effort, if the race was matched to a
     * synced activity. Nullable and intentionally <b>not</b> a foreign key:
     * collector-journal must stay source-agnostic — a race may be logged
     * manually with no linked activity, or linked to an activity from any
     * future source, not necessarily Strava.
     */
    @Column(name = "linked_activity_id")
    private UUID linkedActivityId;

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

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public void setRaceDate(LocalDate raceDate) {
        this.raceDate = raceDate;
    }

    public String getRaceName() {
        return raceName;
    }

    public void setRaceName(String raceName) {
        this.raceName = raceName;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Integer getGoalFinishTime() {
        return goalFinishTime;
    }

    public void setGoalFinishTime(Integer goalFinishTime) {
        this.goalFinishTime = goalFinishTime;
    }

    public Integer getActualFinishTime() {
        return actualFinishTime;
    }

    public void setActualFinishTime(Integer actualFinishTime) {
        this.actualFinishTime = actualFinishTime;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getLinkedActivityId() {
        return linkedActivityId;
    }

    public void setLinkedActivityId(UUID linkedActivityId) {
        this.linkedActivityId = linkedActivityId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
