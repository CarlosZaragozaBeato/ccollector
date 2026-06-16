package com.zensyra.collector.strava.athletestats;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "athlete_stats_snapshots")
public class AthleteStatsSnapshot extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "biggest_ride_distance")
    private Double biggestRideDistance;

    @Column(name = "biggest_climb_elevation_gain")
    private Double biggestClimbElevationGain;

    @Column(name = "ytd_ride_count")
    private Integer ytdRideCount;

    @Column(name = "ytd_ride_distance")
    private Double ytdRideDistance;

    @Column(name = "ytd_ride_moving_time")
    private Integer ytdRideMovingTime;

    @Column(name = "ytd_ride_elapsed_time")
    private Integer ytdRideElapsedTime;

    @Column(name = "ytd_ride_elevation_gain")
    private Double ytdRideElevationGain;

    @Column(name = "ytd_run_count")
    private Integer ytdRunCount;

    @Column(name = "ytd_run_distance")
    private Double ytdRunDistance;

    @Column(name = "ytd_run_moving_time")
    private Integer ytdRunMovingTime;

    @Column(name = "ytd_run_elapsed_time")
    private Integer ytdRunElapsedTime;

    @Column(name = "ytd_run_elevation_gain")
    private Double ytdRunElevationGain;

    @Column(name = "ytd_swim_count")
    private Integer ytdSwimCount;

    @Column(name = "ytd_swim_distance")
    private Double ytdSwimDistance;

    @Column(name = "ytd_swim_moving_time")
    private Integer ytdSwimMovingTime;

    @Column(name = "ytd_swim_elapsed_time")
    private Integer ytdSwimElapsedTime;

    @Column(name = "ytd_swim_elevation_gain")
    private Double ytdSwimElevationGain;

    @Column(name = "all_ride_count")
    private Integer allRideCount;

    @Column(name = "all_ride_distance")
    private Double allRideDistance;

    @Column(name = "all_ride_moving_time")
    private Integer allRideMovingTime;

    @Column(name = "all_ride_elapsed_time")
    private Integer allRideElapsedTime;

    @Column(name = "all_ride_elevation_gain")
    private Double allRideElevationGain;

    @Column(name = "all_run_count")
    private Integer allRunCount;

    @Column(name = "all_run_distance")
    private Double allRunDistance;

    @Column(name = "all_run_moving_time")
    private Integer allRunMovingTime;

    @Column(name = "all_run_elapsed_time")
    private Integer allRunElapsedTime;

    @Column(name = "all_run_elevation_gain")
    private Double allRunElevationGain;

    @Column(name = "all_swim_count")
    private Integer allSwimCount;

    @Column(name = "all_swim_distance")
    private Double allSwimDistance;

    @Column(name = "all_swim_moving_time")
    private Integer allSwimMovingTime;

    @Column(name = "all_swim_elapsed_time")
    private Integer allSwimElapsedTime;

    @Column(name = "all_swim_elevation_gain")
    private Double allSwimElevationGain;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAthleteId() {
        return athleteId;
    }

    public void setAthleteId(Long athleteId) {
        this.athleteId = athleteId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public Double getBiggestRideDistance() {
        return biggestRideDistance;
    }

    public void setBiggestRideDistance(Double biggestRideDistance) {
        this.biggestRideDistance = biggestRideDistance;
    }

    public Double getBiggestClimbElevationGain() {
        return biggestClimbElevationGain;
    }

    public void setBiggestClimbElevationGain(Double biggestClimbElevationGain) {
        this.biggestClimbElevationGain = biggestClimbElevationGain;
    }

    public Integer getYtdRideCount() {
        return ytdRideCount;
    }

    public void setYtdRideCount(Integer ytdRideCount) {
        this.ytdRideCount = ytdRideCount;
    }

    public Double getYtdRideDistance() {
        return ytdRideDistance;
    }

    public void setYtdRideDistance(Double ytdRideDistance) {
        this.ytdRideDistance = ytdRideDistance;
    }

    public Integer getYtdRideMovingTime() {
        return ytdRideMovingTime;
    }

    public void setYtdRideMovingTime(Integer ytdRideMovingTime) {
        this.ytdRideMovingTime = ytdRideMovingTime;
    }

    public Integer getYtdRideElapsedTime() {
        return ytdRideElapsedTime;
    }

    public void setYtdRideElapsedTime(Integer ytdRideElapsedTime) {
        this.ytdRideElapsedTime = ytdRideElapsedTime;
    }

    public Double getYtdRideElevationGain() {
        return ytdRideElevationGain;
    }

    public void setYtdRideElevationGain(Double ytdRideElevationGain) {
        this.ytdRideElevationGain = ytdRideElevationGain;
    }

    public Integer getYtdRunCount() {
        return ytdRunCount;
    }

    public void setYtdRunCount(Integer ytdRunCount) {
        this.ytdRunCount = ytdRunCount;
    }

    public Double getYtdRunDistance() {
        return ytdRunDistance;
    }

    public void setYtdRunDistance(Double ytdRunDistance) {
        this.ytdRunDistance = ytdRunDistance;
    }

    public Integer getYtdRunMovingTime() {
        return ytdRunMovingTime;
    }

    public void setYtdRunMovingTime(Integer ytdRunMovingTime) {
        this.ytdRunMovingTime = ytdRunMovingTime;
    }

    public Integer getYtdRunElapsedTime() {
        return ytdRunElapsedTime;
    }

    public void setYtdRunElapsedTime(Integer ytdRunElapsedTime) {
        this.ytdRunElapsedTime = ytdRunElapsedTime;
    }

    public Double getYtdRunElevationGain() {
        return ytdRunElevationGain;
    }

    public void setYtdRunElevationGain(Double ytdRunElevationGain) {
        this.ytdRunElevationGain = ytdRunElevationGain;
    }

    public Integer getYtdSwimCount() {
        return ytdSwimCount;
    }

    public void setYtdSwimCount(Integer ytdSwimCount) {
        this.ytdSwimCount = ytdSwimCount;
    }

    public Double getYtdSwimDistance() {
        return ytdSwimDistance;
    }

    public void setYtdSwimDistance(Double ytdSwimDistance) {
        this.ytdSwimDistance = ytdSwimDistance;
    }

    public Integer getYtdSwimMovingTime() {
        return ytdSwimMovingTime;
    }

    public void setYtdSwimMovingTime(Integer ytdSwimMovingTime) {
        this.ytdSwimMovingTime = ytdSwimMovingTime;
    }

    public Integer getYtdSwimElapsedTime() {
        return ytdSwimElapsedTime;
    }

    public void setYtdSwimElapsedTime(Integer ytdSwimElapsedTime) {
        this.ytdSwimElapsedTime = ytdSwimElapsedTime;
    }

    public Double getYtdSwimElevationGain() {
        return ytdSwimElevationGain;
    }

    public void setYtdSwimElevationGain(Double ytdSwimElevationGain) {
        this.ytdSwimElevationGain = ytdSwimElevationGain;
    }

    public Integer getAllRideCount() {
        return allRideCount;
    }

    public void setAllRideCount(Integer allRideCount) {
        this.allRideCount = allRideCount;
    }

    public Double getAllRideDistance() {
        return allRideDistance;
    }

    public void setAllRideDistance(Double allRideDistance) {
        this.allRideDistance = allRideDistance;
    }

    public Integer getAllRideMovingTime() {
        return allRideMovingTime;
    }

    public void setAllRideMovingTime(Integer allRideMovingTime) {
        this.allRideMovingTime = allRideMovingTime;
    }

    public Integer getAllRideElapsedTime() {
        return allRideElapsedTime;
    }

    public void setAllRideElapsedTime(Integer allRideElapsedTime) {
        this.allRideElapsedTime = allRideElapsedTime;
    }

    public Double getAllRideElevationGain() {
        return allRideElevationGain;
    }

    public void setAllRideElevationGain(Double allRideElevationGain) {
        this.allRideElevationGain = allRideElevationGain;
    }

    public Integer getAllRunCount() {
        return allRunCount;
    }

    public void setAllRunCount(Integer allRunCount) {
        this.allRunCount = allRunCount;
    }

    public Double getAllRunDistance() {
        return allRunDistance;
    }

    public void setAllRunDistance(Double allRunDistance) {
        this.allRunDistance = allRunDistance;
    }

    public Integer getAllRunMovingTime() {
        return allRunMovingTime;
    }

    public void setAllRunMovingTime(Integer allRunMovingTime) {
        this.allRunMovingTime = allRunMovingTime;
    }

    public Integer getAllRunElapsedTime() {
        return allRunElapsedTime;
    }

    public void setAllRunElapsedTime(Integer allRunElapsedTime) {
        this.allRunElapsedTime = allRunElapsedTime;
    }

    public Double getAllRunElevationGain() {
        return allRunElevationGain;
    }

    public void setAllRunElevationGain(Double allRunElevationGain) {
        this.allRunElevationGain = allRunElevationGain;
    }

    public Integer getAllSwimCount() {
        return allSwimCount;
    }

    public void setAllSwimCount(Integer allSwimCount) {
        this.allSwimCount = allSwimCount;
    }

    public Double getAllSwimDistance() {
        return allSwimDistance;
    }

    public void setAllSwimDistance(Double allSwimDistance) {
        this.allSwimDistance = allSwimDistance;
    }

    public Integer getAllSwimMovingTime() {
        return allSwimMovingTime;
    }

    public void setAllSwimMovingTime(Integer allSwimMovingTime) {
        this.allSwimMovingTime = allSwimMovingTime;
    }

    public Integer getAllSwimElapsedTime() {
        return allSwimElapsedTime;
    }

    public void setAllSwimElapsedTime(Integer allSwimElapsedTime) {
        this.allSwimElapsedTime = allSwimElapsedTime;
    }

    public Double getAllSwimElevationGain() {
        return allSwimElevationGain;
    }

    public void setAllSwimElevationGain(Double allSwimElevationGain) {
        this.allSwimElevationGain = allSwimElevationGain;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
