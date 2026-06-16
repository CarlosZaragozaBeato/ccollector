package com.zensyra.collector.strava.stream;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class ActivityStreamId implements Serializable {

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "elapsed_seconds", nullable = false)
    private Integer elapsedSeconds;

    @Column(name = "time", nullable = false)
    private Instant time;

    public ActivityStreamId() {
    }

    public ActivityStreamId(Long activityId, Instant time, Integer elapsedSeconds) {
        this.activityId = activityId;
        this.time = time;
        this.elapsedSeconds = elapsedSeconds;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Integer getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(Integer elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ActivityStreamId that)) {
            return false;
        }
        return Objects.equals(activityId, that.activityId)
                && Objects.equals(time, that.time)
                && Objects.equals(elapsedSeconds, that.elapsedSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activityId, time, elapsedSeconds);
    }
}
