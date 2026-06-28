package com.zensyra.collector.strava.activity;

import com.zensyra.collector.strava.stream.StreamSyncStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "activities")
public class Activity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strava_id", nullable = false, unique = true)
    private Long stravaId;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column
    private String name;

    @Column(length = 50)
    private String type;

    @Column(name = "sport_type", length = 50)
    private String sportType;

    @Column(precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(name = "moving_time")
    private Integer movingTime;

    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "total_elevation_gain", precision = 8, scale = 2)
    private BigDecimal totalElevationGain;

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

    @Column(precision = 10, scale = 2)
    private BigDecimal kilojoules;

    @Column(name = "suffer_score")
    private Integer sufferScore;

    @Column(nullable = false)
    private boolean trainer = false;

    @Column(nullable = false)
    private boolean commute = false;

    @Column(nullable = false)
    private boolean manual = false;

    @Column(name = "private", nullable = false)
    private boolean privateActivity = false;

    @Column(nullable = false)
    private boolean flagged = false;

    @Column(name = "gear_id", length = 50)
    private String gearId;

    @Column(length = 100)
    private String timezone;

    @Column(name = "start_latlng", length = 50)
    private String startLatlng;

    @Column(name = "end_latlng", length = 50)
    private String endLatlng;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- detail fields ---

    @Column
    private Integer calories;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "perceived_exertion", precision = 3, scale = 1)
    private BigDecimal perceivedExertion;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "streams_synced_at")
    private Instant streamsSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "streams_sync_status", length = 20)
    private StreamSyncStatus streamsSyncStatus;

    @Column(name = "streams_sync_attempts", nullable = false)
    private Integer streamsSyncAttempts = 0;

    @Column(name = "streams_last_error", columnDefinition = "TEXT")
    private String streamsLastError;

    @Column(name = "streams_last_requested_at")
    private Instant streamsLastRequestedAt;

    // getters and setters:
    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPerceivedExertion() { return perceivedExertion; }
    public void setPerceivedExertion(BigDecimal perceivedExertion) { this.perceivedExertion = perceivedExertion; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public Instant getStreamsSyncedAt() { return streamsSyncedAt; }
    public void setStreamsSyncedAt(Instant streamsSyncedAt) { this.streamsSyncedAt = streamsSyncedAt; }

    public StreamSyncStatus getStreamsSyncStatus() { return streamsSyncStatus; }
    public void setStreamsSyncStatus(StreamSyncStatus streamsSyncStatus) { this.streamsSyncStatus = streamsSyncStatus; }

    public Integer getStreamsSyncAttempts() { return streamsSyncAttempts; }
    public void setStreamsSyncAttempts(Integer streamsSyncAttempts) { this.streamsSyncAttempts = streamsSyncAttempts; }

    public String getStreamsLastError() { return streamsLastError; }
    public void setStreamsLastError(String streamsLastError) { this.streamsLastError = streamsLastError; }

    public Instant getStreamsLastRequestedAt() { return streamsLastRequestedAt; }
    public void setStreamsLastRequestedAt(Instant streamsLastRequestedAt) { this.streamsLastRequestedAt = streamsLastRequestedAt; }

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

    public Long getStravaId() { return stravaId; }
    public void setStravaId(Long stravaId) { this.stravaId = stravaId; }

    public Long getAthleteId() { return athleteId; }
    public void setAthleteId(Long athleteId) { this.athleteId = athleteId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSportType() { return sportType; }
    public void setSportType(String sportType) { this.sportType = sportType; }

    public BigDecimal getDistance() { return distance; }
    public void setDistance(BigDecimal distance) { this.distance = distance; }

    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public BigDecimal getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(BigDecimal totalElevationGain) { this.totalElevationGain = totalElevationGain; }

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

    public BigDecimal getKilojoules() { return kilojoules; }
    public void setKilojoules(BigDecimal kilojoules) { this.kilojoules = kilojoules; }

    public Integer getSufferScore() { return sufferScore; }
    public void setSufferScore(Integer sufferScore) { this.sufferScore = sufferScore; }

    public boolean isTrainer() { return trainer; }
    public void setTrainer(boolean trainer) { this.trainer = trainer; }

    public boolean isCommute() { return commute; }
    public void setCommute(boolean commute) { this.commute = commute; }

    public boolean isManual() { return manual; }
    public void setManual(boolean manual) { this.manual = manual; }

    public boolean isPrivateActivity() { return privateActivity; }
    public void setPrivateActivity(boolean privateActivity) { this.privateActivity = privateActivity; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public String getGearId() { return gearId; }
    public void setGearId(String gearId) { this.gearId = gearId; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getStartLatlng() { return startLatlng; }
    public void setStartLatlng(String startLatlng) { this.startLatlng = startLatlng; }

    public String getEndLatlng() { return endLatlng; }
    public void setEndLatlng(String endLatlng) { this.endLatlng = endLatlng; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
