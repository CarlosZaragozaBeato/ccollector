package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.PeriodSummary;
import com.zensyra.collector.query.port.ActivitySummaryQueryPort;
import com.zensyra.collector.strava.summary.ActivitySummaryRow;
import com.zensyra.collector.strava.summary.ActivitySummaryViewRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link ActivitySummaryQueryPort}.
 *
 * <p>Resolution path mirrors {@link StravaTrainingLoadQueryPort}: the canonical
 * {@code AthleteId} is resolved to the athlete's Strava
 * {@link IntegrationAccount}, whose {@code externalUserId} is used to query
 * the TimescaleDB continuous-aggregate views
 * ({@code weekly_activity_summary} / {@code monthly_activity_summary}).
 */
@ApplicationScoped
public class StravaActivitySummaryQueryPort implements ActivitySummaryQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivitySummaryViewRepository activitySummaryViewRepository;

    @Inject
    public StravaActivitySummaryQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivitySummaryViewRepository activitySummaryViewRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activitySummaryViewRepository = activitySummaryViewRepository;
    }

    @Override
    public List<PeriodSummary> listByAthlete(
            UUID athleteId, LocalDate from, LocalDate to, Granularity granularity) {

        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            return List.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        return activitySummaryViewRepository
                .findByAthleteId(stravaAthleteId, from, to, granularity)
                .stream()
                .map(row -> toReadModel(athleteId, row, granularity))
                .toList();
    }

    // Same pattern as StravaActivityQueryPort.resolveStravaAccount.
    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private PeriodSummary toReadModel(UUID athleteId, ActivitySummaryRow row, Granularity granularity) {
        return new PeriodSummary(
                athleteId,
                row.periodStart(),
                granularity,
                row.numActivities(),
                row.totalDistanceMeters(),
                row.totalMovingTimeSecs(),
                row.totalElevationGainMeters()
        );
    }
}
