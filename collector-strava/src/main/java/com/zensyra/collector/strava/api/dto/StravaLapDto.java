package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaLapDto {

    private Long id;
    private String name;

    @JsonProperty("lap_index")
    private Integer lapIndex;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("start_date")
    private String startDate;

    private Float distance;

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

    @JsonProperty("total_elevation_gain")
    private Float totalElevationGain;

    @JsonProperty("pace_zone")
    private Integer paceZone;

    private Integer split;

    @JsonProperty("start_index")
    private Integer startIndex;

    @JsonProperty("end_index")
    private Integer endIndex;

    public StravaLapDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getLapIndex() { return lapIndex; }
    public void setLapIndex(Integer lapIndex) { this.lapIndex = lapIndex; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public Float getDistance() { return distance; }
    public void setDistance(Float distance) { this.distance = distance; }

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

    public Float getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Float totalElevationGain) { this.totalElevationGain = totalElevationGain; }

    public Integer getPaceZone() { return paceZone; }
    public void setPaceZone(Integer paceZone) { this.paceZone = paceZone; }

    public Integer getSplit() { return split; }
    public void setSplit(Integer split) { this.split = split; }

    public Integer getStartIndex() { return startIndex; }
    public void setStartIndex(Integer startIndex) { this.startIndex = startIndex; }

    public Integer getEndIndex() { return endIndex; }
    public void setEndIndex(Integer endIndex) { this.endIndex = endIndex; }
}
