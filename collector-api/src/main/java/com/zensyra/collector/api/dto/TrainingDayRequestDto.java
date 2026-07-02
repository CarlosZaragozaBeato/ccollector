package com.zensyra.collector.api.dto;

import java.time.LocalDate;

public class TrainingDayRequestDto {

    private LocalDate date;
    private Integer perceivedEffort;
    private String subjectiveState;
    private String notes;
    private Double weightKg;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getPerceivedEffort() {
        return perceivedEffort;
    }

    public void setPerceivedEffort(Integer perceivedEffort) {
        this.perceivedEffort = perceivedEffort;
    }

    public String getSubjectiveState() {
        return subjectiveState;
    }

    public void setSubjectiveState(String subjectiveState) {
        this.subjectiveState = subjectiveState;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }
}
