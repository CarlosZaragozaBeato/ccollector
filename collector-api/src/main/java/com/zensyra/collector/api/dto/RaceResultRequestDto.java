package com.zensyra.collector.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public class RaceResultRequestDto {

    private LocalDate raceDate;
    private String raceName;
    private Double distanceMeters;
    private Integer goalFinishTime;
    private Integer actualFinishTime;
    private Integer position;
    private String notes;
    private UUID linkedActivityId;

    public LocalDate getRaceDate() {
        return raceDate;
    }

    public void setRaceDate(LocalDate raceDate) {
        this.raceDate = raceDate;
    }

    public String getRaceName() {
        return raceName;
    }

    public void setRaceName(String raceName) {
        this.raceName = raceName;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Integer getGoalFinishTime() {
        return goalFinishTime;
    }

    public void setGoalFinishTime(Integer goalFinishTime) {
        this.goalFinishTime = goalFinishTime;
    }

    public Integer getActualFinishTime() {
        return actualFinishTime;
    }

    public void setActualFinishTime(Integer actualFinishTime) {
        this.actualFinishTime = actualFinishTime;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getLinkedActivityId() {
        return linkedActivityId;
    }

    public void setLinkedActivityId(UUID linkedActivityId) {
        this.linkedActivityId = linkedActivityId;
    }
}
