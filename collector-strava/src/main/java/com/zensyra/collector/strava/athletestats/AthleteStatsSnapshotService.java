package com.zensyra.collector.strava.athletestats;

import com.zensyra.collector.strava.api.dto.StravaAthleteStatsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class AthleteStatsSnapshotService {

    private static final Logger LOG = Logger.getLogger(AthleteStatsSnapshotService.class);

    @Inject
    AthleteStatsSnapshotRepository snapshotRepository;

    @Transactional
    public void upsertDailySnapshot(Long athleteId, LocalDate snapshotDate, StravaAthleteStatsDto dto) {
        Optional<AthleteStatsSnapshot> existing = snapshotRepository.findByAthleteAndDate(athleteId, snapshotDate);
        AthleteStatsSnapshot snapshot = existing.orElseGet(AthleteStatsSnapshot::new);

        snapshot.setAthleteId(athleteId);
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setBiggestRideDistance(dto != null ? dto.getBiggestRideDistance() : null);
        snapshot.setBiggestClimbElevationGain(dto != null ? dto.getBiggestClimbElevationGain() : null);

        applyTotals(
                snapshot,
                dto != null ? dto.getYtdRideTotals() : null,
                dto != null ? dto.getYtdRunTotals() : null,
                dto != null ? dto.getYtdSwimTotals() : null,
                dto != null ? dto.getAllRideTotals() : null,
                dto != null ? dto.getAllRunTotals() : null,
                dto != null ? dto.getAllSwimTotals() : null
        );

        if (existing.isEmpty()) {
            snapshotRepository.persist(snapshot);
        }

        LOG.infof("Athlete stats snapshot guardado — athleteId=%d date=%s", athleteId, snapshotDate);
    }

    private void applyTotals(
            AthleteStatsSnapshot snapshot,
            StravaAthleteStatsDto.StatsTotals ytdRide,
            StravaAthleteStatsDto.StatsTotals ytdRun,
            StravaAthleteStatsDto.StatsTotals ytdSwim,
            StravaAthleteStatsDto.StatsTotals allRide,
            StravaAthleteStatsDto.StatsTotals allRun,
            StravaAthleteStatsDto.StatsTotals allSwim
    ) {
        snapshot.setYtdRideCount(value(ytdRide, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setYtdRideDistance(value(ytdRide, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setYtdRideMovingTime(value(ytdRide, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setYtdRideElapsedTime(value(ytdRide, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setYtdRideElevationGain(value(ytdRide, StravaAthleteStatsDto.StatsTotals::getElevationGain));

        snapshot.setYtdRunCount(value(ytdRun, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setYtdRunDistance(value(ytdRun, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setYtdRunMovingTime(value(ytdRun, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setYtdRunElapsedTime(value(ytdRun, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setYtdRunElevationGain(value(ytdRun, StravaAthleteStatsDto.StatsTotals::getElevationGain));

        snapshot.setYtdSwimCount(value(ytdSwim, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setYtdSwimDistance(value(ytdSwim, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setYtdSwimMovingTime(value(ytdSwim, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setYtdSwimElapsedTime(value(ytdSwim, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setYtdSwimElevationGain(value(ytdSwim, StravaAthleteStatsDto.StatsTotals::getElevationGain));

        snapshot.setAllRideCount(value(allRide, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setAllRideDistance(value(allRide, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setAllRideMovingTime(value(allRide, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setAllRideElapsedTime(value(allRide, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setAllRideElevationGain(value(allRide, StravaAthleteStatsDto.StatsTotals::getElevationGain));

        snapshot.setAllRunCount(value(allRun, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setAllRunDistance(value(allRun, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setAllRunMovingTime(value(allRun, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setAllRunElapsedTime(value(allRun, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setAllRunElevationGain(value(allRun, StravaAthleteStatsDto.StatsTotals::getElevationGain));

        snapshot.setAllSwimCount(value(allSwim, StravaAthleteStatsDto.StatsTotals::getCount));
        snapshot.setAllSwimDistance(value(allSwim, StravaAthleteStatsDto.StatsTotals::getDistance));
        snapshot.setAllSwimMovingTime(value(allSwim, StravaAthleteStatsDto.StatsTotals::getMovingTime));
        snapshot.setAllSwimElapsedTime(value(allSwim, StravaAthleteStatsDto.StatsTotals::getElapsedTime));
        snapshot.setAllSwimElevationGain(value(allSwim, StravaAthleteStatsDto.StatsTotals::getElevationGain));
    }

    private <T> T value(StravaAthleteStatsDto.StatsTotals totals, java.util.function.Function<StravaAthleteStatsDto.StatsTotals, T> getter) {
        return totals != null ? getter.apply(totals) : null;
    }
}
