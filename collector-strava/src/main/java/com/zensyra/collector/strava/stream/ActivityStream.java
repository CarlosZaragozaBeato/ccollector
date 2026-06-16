package com.zensyra.collector.strava.stream;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "activity_streams")
public class ActivityStream extends PanacheEntityBase {

    @EmbeddedId
    private ActivityStreamId id;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "heartrate_bpm")
    private Integer heartrateBpm;

    @Column(name = "watts")
    private Integer watts;

    @Column(name = "cadence_rpm")
    private Integer cadenceRpm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public ActivityStreamId getId() {
        return id;
    }

    public void setId(ActivityStreamId id) {
        this.id = id;
    }

    public Long getAthleteId() {
        return athleteId;
    }

    public void setAthleteId(Long athleteId) {
        this.athleteId = athleteId;
    }

    public Instant getTime() {
        return id != null ? id.getTime() : null;
    }

    public void setTime(Instant time) {
        if (id == null) {
            id = new ActivityStreamId();
        }
        id.setTime(time);
    }

    public Double getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(Double distanceM) {
        this.distanceM = distanceM;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAltitudeM() {
        return altitudeM;
    }

    public void setAltitudeM(Double altitudeM) {
        this.altitudeM = altitudeM;
    }

    public Integer getHeartrateBpm() {
        return heartrateBpm;
    }

    public void setHeartrateBpm(Integer heartrateBpm) {
        this.heartrateBpm = heartrateBpm;
    }

    public Integer getWatts() {
        return watts;
    }

    public void setWatts(Integer watts) {
        this.watts = watts;
    }

    public Integer getCadenceRpm() {
        return cadenceRpm;
    }

    public void setCadenceRpm(Integer cadenceRpm) {
        this.cadenceRpm = cadenceRpm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
