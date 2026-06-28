package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link TrainingLoadQueryPort}.
 *
 * <p>Resolution path mirrors {@link StravaActivityQueryPort}: the canonical
 * {@code AthleteId} is resolved to this athlete's Strava
 * {@link IntegrationAccount}, whose {@code externalUserId} is used to query
 * {@code collector-strava}'s own training load rows. No further
 * per-activity identity translation is needed here — these rows are
 * athlete-scoped daily snapshots, not tied to any individual activity.
 */
@ApplicationScoped
public class StravaTrainingLoadQueryPort implements TrainingLoadQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final AthleteTrainingLoadRepository athleteTrainingLoadRepository;

    @Inject
    public StravaTrainingLoadQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            AthleteTrainingLoadRepository athleteTrainingLoadRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.athleteTrainingLoadRepository = athleteTrainingLoadRepository;
    }

    @Override
    public List<TrainingLoad> listRecentByAthlete(UUID athleteId, LocalDate from) {
        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            return List.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        return athleteTrainingLoadRepository.findRecentByAthleteId(stravaAthleteId, from)
                .stream()
                .map(record -> toReadModel(athleteId, record))
                .toList();
    }

    // Deliberately not optimized with a more specific repository method —
    // same reasoning as StravaActivityQueryPort.resolveStravaAccount.
    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private TrainingLoad toReadModel(
            UUID athleteId, com.zensyra.collector.strava.trainingload.AthleteTrainingLoad record) {
        return new TrainingLoad(
                athleteId,
                record.getDate(),
                record.getTssDay(),
                record.getCtl(),
                record.getAtl(),
                record.getTsb()
        );
    }
}
