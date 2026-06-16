package com.zensyra.collector.strava.route;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "routes")
public class Route extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strava_id", nullable = false, unique = true)
    private Long stravaId;

    @Column(name = "athlete_id", nullable = false)
    private Long athleteId;

    @Column(nullable = false)
    private String name;

    @Column
    private Float distance;

    @Column(name = "elevation_gain")
    private Float elevationGain;

    @Column
    private Integer type;

    @Column(columnDefinition = "TEXT")
    private String polyline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onPreUpdate() {
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

    public Float getDistance() { return distance; }
    public void setDistance(Float distance) { this.distance = distance; }

    public Float getElevationGain() { return elevationGain; }
    public void setElevationGain(Float elevationGain) { this.elevationGain = elevationGain; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getPolyline() { return polyline; }
    public void setPolyline(String polyline) { this.polyline = polyline; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
