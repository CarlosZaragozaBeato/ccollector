package com.zensyra.collector.api.dto;

import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshot;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;

@RegisterForReflection
public record AthleteStatsDto(
        Long athleteId,
        LocalDate snapshotDate,
        Integer ytdRideCount,
        Double ytdRideDistanceKm,
        Integer ytdRideMovingTimeSecs,
        Double ytdRideElevationGain,
        Integer ytdRunCount,
        Double ytdRunDistanceKm,
        Integer ytdRunMovingTimeSecs,
        Integer allRideCount,
        Double allRideDistanceKm,
        Integer allRunCount,
        Double allRunDistanceKm
) {
    public static AthleteStatsDto from(AthleteStatsSnapshot s) {
        return new AthleteStatsDto(
                s.getAthleteId(),
                s.getSnapshotDate(),
                s.getYtdRideCount(),
                metersToKm(s.getYtdRideDistance()),
                s.getYtdRideMovingTime(),
                s.getYtdRideElevationGain(),
                s.getYtdRunCount(),
                metersToKm(s.getYtdRunDistance()),
                s.getYtdRunMovingTime(),
                s.getAllRideCount(),
                metersToKm(s.getAllRideDistance()),
                s.getAllRunCount(),
                metersToKm(s.getAllRunDistance())
        );
    }

    private static Double metersToKm(Double meters) {
        return meters != null ? Math.round(meters / 10.0) / 100.0 : null;
    }
}
