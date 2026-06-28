package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.AthleteStats;
import com.zensyra.collector.query.model.SportAggregate;
import com.zensyra.collector.query.model.StatsWindow;
import com.zensyra.collector.query.port.AthleteStatsQueryPort;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshot;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link AthleteStatsQueryPort}.
 *
 * <p>{@code AthleteStatsSnapshot} stores six fixed (sport, window) pairs as
 * separate columns (Ride/Run/Swim × year-to-date/all-time). This adapter
 * flattens those columns into the canonical
 * {@code List<SportAggregate>} shape — the only place in the codebase that
 * needs to know Strava's column layout for this data. A (sport, window)
 * pair is omitted entirely when Strava never recorded an activity count for
 * it, rather than emitting an aggregate with all-null totals.
 */
@ApplicationScoped
public class StravaAthleteStatsQueryPort implements AthleteStatsQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final AthleteStatsSnapshotRepository athleteStatsSnapshotRepository;

    @Inject
    public StravaAthleteStatsQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            AthleteStatsSnapshotRepository athleteStatsSnapshotRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.athleteStatsSnapshotRepository = athleteStatsSnapshotRepository;
    }

    @Override
    public Optional<AthleteStats> getLatestByAthlete(UUID athleteId) {
        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            return Optional.empty();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        return athleteStatsSnapshotRepository.findLatestByAthleteId(stravaAthleteId)
                .map(snapshot -> toReadModel(athleteId, snapshot));
    }

    // Deliberately not optimized with a more specific repository method —
    // same reasoning as StravaActivityQueryPort.resolveStravaAccount.
    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private AthleteStats toReadModel(UUID athleteId, AthleteStatsSnapshot snapshot) {
        List<SportAggregate> aggregates = new ArrayList<>();

        addIfPresent(aggregates, "Ride", StatsWindow.YEAR_TO_DATE,
                snapshot.getYtdRideCount(), snapshot.getYtdRideDistance(),
                snapshot.getYtdRideMovingTime(), snapshot.getYtdRideElevationGain());
        addIfPresent(aggregates, "Ride", StatsWindow.ALL_TIME,
                snapshot.getAllRideCount(), snapshot.getAllRideDistance(),
                snapshot.getAllRideMovingTime(), snapshot.getAllRideElevationGain());

        addIfPresent(aggregates, "Run", StatsWindow.YEAR_TO_DATE,
                snapshot.getYtdRunCount(), snapshot.getYtdRunDistance(),
                snapshot.getYtdRunMovingTime(), snapshot.getYtdRunElevationGain());
        addIfPresent(aggregates, "Run", StatsWindow.ALL_TIME,
                snapshot.getAllRunCount(), snapshot.getAllRunDistance(),
                snapshot.getAllRunMovingTime(), snapshot.getAllRunElevationGain());

        addIfPresent(aggregates, "Swim", StatsWindow.YEAR_TO_DATE,
                snapshot.getYtdSwimCount(), snapshot.getYtdSwimDistance(),
                snapshot.getYtdSwimMovingTime(), snapshot.getYtdSwimElevationGain());
        addIfPresent(aggregates, "Swim", StatsWindow.ALL_TIME,
                snapshot.getAllSwimCount(), snapshot.getAllSwimDistance(),
                snapshot.getAllSwimMovingTime(), snapshot.getAllSwimElevationGain());

        return new AthleteStats(athleteId, snapshot.getSnapshotDate(), aggregates);
    }

    private void addIfPresent(
            List<SportAggregate> aggregates,
            String sportType,
            StatsWindow window,
            Integer activityCount,
            Double distanceMeters,
            Integer movingTimeSecs,
            Double elevationGainMeters) {
        if (activityCount == null) {
            return;
        }
        aggregates.add(new SportAggregate(
                sportType, window, activityCount, distanceMeters, movingTimeSecs, elevationGainMeters));
    }
}
