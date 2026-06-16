package com.zensyra.collector.strava.activitymetrics;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "activity_metrics")
public class ActivityMetrics extends PanacheEntityBase {

    @Id
    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "normalized_power", precision = 8, scale = 2)
    private BigDecimal normalizedPower;

    @Column(name = "variability_index", precision = 6, scale = 4)
    private BigDecimal variabilityIndex;

    @Column(name = "efficiency_factor", precision = 6, scale = 4)
    private BigDecimal efficiencyFactor;

    @Column(name = "intensity_factor", precision = 6, scale = 4)
    private BigDecimal intensityFactor;

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

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }

    public BigDecimal getNormalizedPower() { return normalizedPower; }
    public void setNormalizedPower(BigDecimal normalizedPower) { this.normalizedPower = normalizedPower; }

    public BigDecimal getVariabilityIndex() { return variabilityIndex; }
    public void setVariabilityIndex(BigDecimal variabilityIndex) { this.variabilityIndex = variabilityIndex; }

    public BigDecimal getEfficiencyFactor() { return efficiencyFactor; }
    public void setEfficiencyFactor(BigDecimal efficiencyFactor) { this.efficiencyFactor = efficiencyFactor; }

    public BigDecimal getIntensityFactor() { return intensityFactor; }
    public void setIntensityFactor(BigDecimal intensityFactor) { this.intensityFactor = intensityFactor; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
