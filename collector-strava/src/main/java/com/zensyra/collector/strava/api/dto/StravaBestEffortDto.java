package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaBestEffortDto {

    private Long id;
    private String name;
    private Integer distance;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;

    @JsonProperty("is_kom")
    private Boolean isKom;

    @JsonProperty("pr_rank")
    private Integer prRank;

    public StravaBestEffortDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDistance() { return distance; }
    public void setDistance(Integer distance) { this.distance = distance; }

    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }

    public Boolean getIsKom() { return isKom; }
    public void setIsKom(Boolean isKom) { this.isKom = isKom; }

    public Integer getPrRank() { return prRank; }
    public void setPrRank(Integer prRank) { this.prRank = prRank; }
}
