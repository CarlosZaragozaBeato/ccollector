package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaAthleteStatsDto {

    @JsonProperty("biggest_ride_distance")
    private Double biggestRideDistance;

    @JsonProperty("biggest_climb_elevation_gain")
    private Double biggestClimbElevationGain;

    @JsonProperty("ytd_ride_totals")
    private StatsTotals ytdRideTotals;

    @JsonProperty("ytd_run_totals")
    private StatsTotals ytdRunTotals;

    @JsonProperty("ytd_swim_totals")
    private StatsTotals ytdSwimTotals;

    @JsonProperty("all_ride_totals")
    private StatsTotals allRideTotals;

    @JsonProperty("all_run_totals")
    private StatsTotals allRunTotals;

    @JsonProperty("all_swim_totals")
    private StatsTotals allSwimTotals;

    public Double getBiggestRideDistance() {
        return biggestRideDistance;
    }

    public void setBiggestRideDistance(Double biggestRideDistance) {
        this.biggestRideDistance = biggestRideDistance;
    }

    public Double getBiggestClimbElevationGain() {
        return biggestClimbElevationGain;
    }

    public void setBiggestClimbElevationGain(Double biggestClimbElevationGain) {
        this.biggestClimbElevationGain = biggestClimbElevationGain;
    }

    public StatsTotals getYtdRideTotals() {
        return ytdRideTotals;
    }

    public void setYtdRideTotals(StatsTotals ytdRideTotals) {
        this.ytdRideTotals = ytdRideTotals;
    }

    public StatsTotals getYtdRunTotals() {
        return ytdRunTotals;
    }

    public void setYtdRunTotals(StatsTotals ytdRunTotals) {
        this.ytdRunTotals = ytdRunTotals;
    }

    public StatsTotals getYtdSwimTotals() {
        return ytdSwimTotals;
    }

    public void setYtdSwimTotals(StatsTotals ytdSwimTotals) {
        this.ytdSwimTotals = ytdSwimTotals;
    }

    public StatsTotals getAllRideTotals() {
        return allRideTotals;
    }

    public void setAllRideTotals(StatsTotals allRideTotals) {
        this.allRideTotals = allRideTotals;
    }

    public StatsTotals getAllRunTotals() {
        return allRunTotals;
    }

    public void setAllRunTotals(StatsTotals allRunTotals) {
        this.allRunTotals = allRunTotals;
    }

    public StatsTotals getAllSwimTotals() {
        return allSwimTotals;
    }

    public void setAllSwimTotals(StatsTotals allSwimTotals) {
        this.allSwimTotals = allSwimTotals;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatsTotals {

        private Integer count;
        private Double distance;

        @JsonProperty("moving_time")
        private Integer movingTime;

        @JsonProperty("elapsed_time")
        private Integer elapsedTime;

        @JsonProperty("elevation_gain")
        private Double elevationGain;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Double getDistance() {
            return distance;
        }

        public void setDistance(Double distance) {
            this.distance = distance;
        }

        public Integer getMovingTime() {
            return movingTime;
        }

        public void setMovingTime(Integer movingTime) {
            this.movingTime = movingTime;
        }

        public Integer getElapsedTime() {
            return elapsedTime;
        }

        public void setElapsedTime(Integer elapsedTime) {
            this.elapsedTime = elapsedTime;
        }

        public Double getElevationGain() {
            return elevationGain;
        }

        public void setElevationGain(Double elevationGain) {
            this.elevationGain = elevationGain;
        }
    }
}
