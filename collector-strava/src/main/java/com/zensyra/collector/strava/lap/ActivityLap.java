package com.zensyra.collector.strava.lap;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "activity_laps")
public class ActivityLap extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_strava_id", nullable = false)
    private Long activityStravaId;

    @Column(name = "lap_index", nullable = false)
    private Integer lapIndex;

    @Column
    private String name;

    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    @Column(name = "moving_time")
    private Integer movingTime;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(name = "average_speed", precision = 8, scale = 4)
    private BigDecimal averageSpeed;

    @Column(name = "max_speed", precision = 8, scale = 4)
    private BigDecimal maxSpeed;

    @Column(name = "average_heartrate", precision = 5, scale = 2)
    private BigDecimal averageHeartrate;

    @Column(name = "max_heartrate", precision = 5, scale = 2)
    private BigDecimal maxHeartrate;

    @Column(name = "average_watts", precision = 8, scale = 2)
    private BigDecimal averageWatts;

    @Column(name = "total_elevation_gain", precision = 8, scale = 2)
    private BigDecimal totalElevationGain;

    @Column(name = "pace_zone")
    private Integer paceZone;

    @Column
    private Integer split;

    @Column(name = "start_index")
    private Integer startIndex;

    @Column(name = "end_index")
    private Integer endIndex;

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

    public Integer getLapIndex() { return lapIndex; }
    public void setLapIndex(Integer lapIndex) { this.lapIndex = lapIndex; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public BigDecimal getDistance() { return distance; }
    public void setDistance(BigDecimal distance) { this.distance = distance; }

    public BigDecimal getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(BigDecimal averageSpeed) { this.averageSpeed = averageSpeed; }

    public BigDecimal getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(BigDecimal maxSpeed) { this.maxSpeed = maxSpeed; }

    public BigDecimal getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(BigDecimal averageHeartrate) { this.averageHeartrate = averageHeartrate; }

    public BigDecimal getMaxHeartrate() { return maxHeartrate; }
    public void setMaxHeartrate(BigDecimal maxHeartrate) { this.maxHeartrate = maxHeartrate; }

    public BigDecimal getAverageWatts() { return averageWatts; }
    public void setAverageWatts(BigDecimal averageWatts) { this.averageWatts = averageWatts; }

    public BigDecimal getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(BigDecimal totalElevationGain) { this.totalElevationGain = totalElevationGain; }

    public Integer getPaceZone() { return paceZone; }
    public void setPaceZone(Integer paceZone) { this.paceZone = paceZone; }

    public Integer getSplit() { return split; }
    public void setSplit(Integer split) { this.split = split; }

    public Integer getStartIndex() { return startIndex; }
    public void setStartIndex(Integer startIndex) { this.startIndex = startIndex; }

    public Integer getEndIndex() { return endIndex; }
    public void setEndIndex(Integer endIndex) { this.endIndex = endIndex; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
