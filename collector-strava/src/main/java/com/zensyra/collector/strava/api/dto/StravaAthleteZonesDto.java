package com.zensyra.collector.strava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaAthleteZonesDto {

    @JsonProperty("heart_rate")
    private ZoneSet heartRate;

    private ZoneSet power;

    public ZoneSet getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(ZoneSet heartRate) {
        this.heartRate = heartRate;
    }

    public ZoneSet getPower() {
        return power;
    }

    public void setPower(ZoneSet power) {
        this.power = power;
    }

    public List<Zone> getHeartRateZones() {
        return heartRate != null && heartRate.getZones() != null
                ? heartRate.getZones()
                : Collections.emptyList();
    }

    public List<Zone> getPowerZones() {
        return power != null && power.getZones() != null
                ? power.getZones()
                : Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZoneSet {
        private List<Zone> zones;

        public List<Zone> getZones() {
            return zones;
        }

        public void setZones(List<Zone> zones) {
            this.zones = zones;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Zone {
        private Integer min;
        private Integer max;

        public Integer getMin() {
            return min;
        }

        public void setMin(Integer min) {
            this.min = min;
        }

        public Integer getMax() {
            return max;
        }

        public void setMax(Integer max) {
            this.max = max;
        }
    }
}
