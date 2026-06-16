package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaRouteDto {

    private Long id;
    private String name;
    private Float distance;

    @JsonProperty("elevation_gain")
    private Float elevationGain;

    private Integer type;

    @JsonProperty("athlete")
    private Map<String, Object> athlete;

    @JsonProperty("map")
    private Map<String, Object> map;

    @JsonProperty("created_at")
    private String createdAt;

    public StravaRouteDto() {}

    public Long getAthleteId() {
        if (athlete == null) return null;
        Object athleteId = athlete.get("id");
        return athleteId instanceof Number n ? n.longValue() : null;
    }

    public String getSummaryPolyline() {
        if (map == null) return null;
        Object polyline = map.get("summary_polyline");
        return polyline instanceof String s ? s : null;
    }

    public Instant getCreatedAtAsInstant() {
        if (createdAt == null) return null;
        return Instant.parse(createdAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Float getDistance() { return distance; }
    public void setDistance(Float distance) { this.distance = distance; }

    public Float getElevationGain() { return elevationGain; }
    public void setElevationGain(Float elevationGain) { this.elevationGain = elevationGain; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
