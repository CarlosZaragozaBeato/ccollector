package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityDto {

    private Long id;
    private String name;
    private String type;

    @JsonProperty("sport_type")
    private String sportType;

    private float distance;

    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("total_elevation_gain")
    private Float totalElevationGain;

    @JsonProperty("average_speed")
    private Float averageSpeed;

    @JsonProperty("max_speed")
    private Float maxSpeed;

    @JsonProperty("average_heartrate")
    private Float averageHeartrate;

    @JsonProperty("max_heartrate")
    private Float maxHeartrate;

    @JsonProperty("average_watts")
    private Float averageWatts;

    private Float kilojoules;

    @JsonProperty("suffer_score")
    private Integer sufferScore;

    private boolean trainer;
    private boolean commute;
    private boolean manual;

    @JsonProperty("private")
    private boolean privateActivity;

    private boolean flagged;

    @JsonProperty("gear_id")
    private String gearId;

    private String timezone;

    @JsonProperty("start_latlng")
    private List<Double> startLatlng;

    @JsonProperty("end_latlng")
    private List<Double> endLatlng;

    @JsonProperty("athlete")
    private Map<String, Object> athlete;

    public StravaActivityDto() {}

    // --- athlete_id helper ---
    public Long getAthleteId() {
        if (athlete == null) return null;
        Object id = athlete.get("id");
        return id instanceof Number n ? n.longValue() : null;
    }

    // --- start/end latlng como string "lat,lng" ---
    public String getStartLatlngAsString() {
        if (startLatlng == null || startLatlng.size() < 2) return null;
        return startLatlng.get(0) + "," + startLatlng.get(1);
    }

    public String getEndLatlngAsString() {
        if (endLatlng == null || endLatlng.size() < 2) return null;
        return endLatlng.get(0) + "," + endLatlng.get(1);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSportType() { return sportType; }
    public void setSportType(String sportType) { this.sportType = sportType; }

    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = distance; }

    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public Float getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Float totalElevationGain) { this.totalElevationGain = totalElevationGain; }

    public Float getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(Float averageSpeed) { this.averageSpeed = averageSpeed; }

    public Float getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(Float maxSpeed) { this.maxSpeed = maxSpeed; }

    public Float getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(Float averageHeartrate) { this.averageHeartrate = averageHeartrate; }

    public Float getMaxHeartrate() { return maxHeartrate; }
    public void setMaxHeartrate(Float maxHeartrate) { this.maxHeartrate = maxHeartrate; }

    public Float getAverageWatts() { return averageWatts; }
    public void setAverageWatts(Float averageWatts) { this.averageWatts = averageWatts; }

    public Float getKilojoules() { return kilojoules; }
    public void setKilojoules(Float kilojoules) { this.kilojoules = kilojoules; }

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
}
