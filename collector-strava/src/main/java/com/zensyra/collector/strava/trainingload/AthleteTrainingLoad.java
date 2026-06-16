package com.zensyra.collector.strava.trainingload;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "athlete_training_load")
public class AthleteTrainingLoad extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "tss_day")
    private Double tssDay;

    @Column(name = "ctl")
    private Double ctl;

    @Column(name = "atl")
    private Double atl;

    @Column(name = "tsb")
    private Double tsb;

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

    public Long getAthleteId() { return athleteId; }
    public void setAthleteId(Long athleteId) { this.athleteId = athleteId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getTssDay() { return tssDay; }
    public void setTssDay(Double tssDay) { this.tssDay = tssDay; }

    public Double getCtl() { return ctl; }
    public void setCtl(Double ctl) { this.ctl = ctl; }

    public Double getAtl() { return atl; }
    public void setAtl(Double atl) { this.atl = atl; }

    public Double getTsb() { return tsb; }
    public void setTsb(Double tsb) { this.tsb = tsb; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
