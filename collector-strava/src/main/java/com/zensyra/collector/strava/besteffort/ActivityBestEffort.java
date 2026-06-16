package com.zensyra.collector.strava.besteffort;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "activity_best_efforts")
public class ActivityBestEffort extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_strava_id", nullable = false)
    private Long activityStravaId;

    @Column(nullable = false)
    private String name;

    @Column
    private Integer distance;

    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    @Column(name = "is_kom")
    private Boolean isKom;

    @Column(name = "pr_rank")
    private Integer prRank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getActivityStravaId() { return activityStravaId; }
    public void setActivityStravaId(Long activityStravaId) { this.activityStravaId = activityStravaId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDistance() { return distance; }
    public void setDistance(Integer distance) { this.distance = distance; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public Boolean getIsKom() { return isKom; }
    public void setIsKom(Boolean isKom) { this.isKom = isKom; }

    public Integer getPrRank() { return prRank; }
    public void setPrRank(Integer prRank) { this.prRank = prRank; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
