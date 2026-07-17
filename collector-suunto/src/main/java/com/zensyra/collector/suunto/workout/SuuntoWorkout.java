package com.zensyra.collector.suunto.workout;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Suunto's own record of one synced workout. Mirrors collector-strava's
 * {@code Activity} entity role: source-owned storage that the query ports
 * translate to the neutral read-models, keyed here by {@code workoutKey}
 * (the natural key every Suunto detail endpoint and webhook uses) — never
 * by the pre-initialized JPA id (#36).
 *
 * <p>Columns hold what #6's mapper decided to keep: the neutral Activity
 * fields (name is always null in Suunto's list payload and is not stored),
 * the four derived metrics, and the selected per-workout training-stress
 * contribution ({@code tss} + method). The daily cross-source EMA is
 * deliberately NOT computed here — recomputing day totals as a sum over
 * these rows is what makes re-syncs structurally unable to double-count.
 * Everything in #6's drop table stays transient on the DTO.
 *
 * <p>{@code lastModified} is Suunto's epoch-milliseconds modification stamp,
 * stored verbatim: it is the incremental-sync watermark
 * ({@code SyncSuuntoWorkoutsJob} resumes from {@code max(lastModified)}).
 */
@Entity
@Table(name = "suunto_workouts")
public class SuuntoWorkout extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workout_key", nullable = false, unique = true)
    private String workoutKey;

    @Column(name = "suunto_user", nullable = false)
    private String suuntoUser;

    /** Raw Suunto sport id, kept for fidelity next to the mapped name. */
    @Column(name = "activity_id")
    private Integer activityId;

    @Column(name = "sport_type", length = 50)
    private String sportType;

    @Column(name = "total_distance")
    private Double totalDistance;

    @Column(name = "moving_time_secs")
    private Integer movingTimeSecs;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "total_elevation_gain")
    private Double totalElevationGain;

    @Column(name = "average_heartrate")
    private Double averageHeartrate;

    @Column(name = "average_watts")
    private Double averageWatts;

    @Column(name = "normalized_power", precision = 10, scale = 4)
    private BigDecimal normalizedPower;

    @Column(name = "variability_index", precision = 10, scale = 6)
    private BigDecimal variabilityIndex;

    @Column(name = "efficiency_factor", precision = 10, scale = 6)
    private BigDecimal efficiencyFactor;

    @Column(name = "intensity_factor", precision = 10, scale = 6)
    private BigDecimal intensityFactor;

    /** Per-workout training-stress contribution, from Suunto's own tssList. */
    @Column(name = "tss")
    private Double tss;

    @Column(name = "tss_calculation_method", length = 20)
    private String tssCalculationMethod;

    @Column(name = "last_modified")
    private Long lastModified;

    @Column(name = "created_at", nullable = false)
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

    public String getWorkoutKey() { return workoutKey; }
    public void setWorkoutKey(String workoutKey) { this.workoutKey = workoutKey; }

    public String getSuuntoUser() { return suuntoUser; }
    public void setSuuntoUser(String suuntoUser) { this.suuntoUser = suuntoUser; }

    public Integer getActivityId() { return activityId; }
    public void setActivityId(Integer activityId) { this.activityId = activityId; }

    public String getSportType() { return sportType; }
    public void setSportType(String sportType) { this.sportType = sportType; }

    public Double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(Double totalDistance) { this.totalDistance = totalDistance; }

    public Integer getMovingTimeSecs() { return movingTimeSecs; }
    public void setMovingTimeSecs(Integer movingTimeSecs) { this.movingTimeSecs = movingTimeSecs; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Double getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Double totalElevationGain) { this.totalElevationGain = totalElevationGain; }

    public Double getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(Double averageHeartrate) { this.averageHeartrate = averageHeartrate; }

    public Double getAverageWatts() { return averageWatts; }
    public void setAverageWatts(Double averageWatts) { this.averageWatts = averageWatts; }

    public BigDecimal getNormalizedPower() { return normalizedPower; }
    public void setNormalizedPower(BigDecimal normalizedPower) { this.normalizedPower = normalizedPower; }

    public BigDecimal getVariabilityIndex() { return variabilityIndex; }
    public void setVariabilityIndex(BigDecimal variabilityIndex) { this.variabilityIndex = variabilityIndex; }

    public BigDecimal getEfficiencyFactor() { return efficiencyFactor; }
    public void setEfficiencyFactor(BigDecimal efficiencyFactor) { this.efficiencyFactor = efficiencyFactor; }

    public BigDecimal getIntensityFactor() { return intensityFactor; }
    public void setIntensityFactor(BigDecimal intensityFactor) { this.intensityFactor = intensityFactor; }

    public Double getTss() { return tss; }
    public void setTss(Double tss) { this.tss = tss; }

    public String getTssCalculationMethod() { return tssCalculationMethod; }
    public void setTssCalculationMethod(String tssCalculationMethod) { this.tssCalculationMethod = tssCalculationMethod; }

    public Long getLastModified() { return lastModified; }
    public void setLastModified(Long lastModified) { this.lastModified = lastModified; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
