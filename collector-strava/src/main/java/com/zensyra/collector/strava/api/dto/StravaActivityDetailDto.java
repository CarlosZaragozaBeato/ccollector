package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityDetailDto {

    private Long id;
    private Integer calories;
    private String description;

    @JsonProperty("perceived_exertion")
    private Float perceivedExertion;

    @JsonProperty("device_name")
    private String deviceName;

    @JsonProperty("laps")
    private List<StravaLapDto> laps;

    @JsonProperty("best_efforts")
    private List<StravaBestEffortDto> bestEfforts;

    // getter/setter:
    public List<StravaLapDto> getLaps() {
        return laps != null ? laps : Collections.emptyList();
    }
    public void setLaps(List<StravaLapDto> laps) { this.laps = laps; }

    public List<StravaBestEffortDto> getBestEfforts() {
        return bestEfforts != null ? bestEfforts : Collections.emptyList();
    }
    public void setBestEfforts(List<StravaBestEffortDto> bestEfforts) { this.bestEfforts = bestEfforts; }

    public StravaActivityDetailDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Float getPerceivedExertion() { return perceivedExertion; }
    public void setPerceivedExertion(Float perceivedExertion) { this.perceivedExertion = perceivedExertion; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
}
