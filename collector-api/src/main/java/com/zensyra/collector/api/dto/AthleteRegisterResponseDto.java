package com.zensyra.collector.api.dto;

import java.time.Instant;

public class AthleteRegisterResponseDto {

    private String athleteId;
    private boolean created;
    private Instant expiresAt;

    public AthleteRegisterResponseDto() {
    }

    public AthleteRegisterResponseDto(String athleteId, boolean created, Instant expiresAt) {
        this.athleteId = athleteId;
        this.created = created;
        this.expiresAt = expiresAt;
    }

    public String getAthleteId() {
        return athleteId;
    }

    public void setAthleteId(String athleteId) {
        this.athleteId = athleteId;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
