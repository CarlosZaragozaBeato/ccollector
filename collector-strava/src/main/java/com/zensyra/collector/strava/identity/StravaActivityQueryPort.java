package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Activity;
import com.zensyra.collector.query.port.ActivityQueryPort;
import com.zensyra.collector.strava.activity.ActivityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link ActivityQueryPort}. This is the only place
 * in {@code collector-strava} allowed to translate a canonical
 * {@code AthleteId} into Strava's own identifiers and back into a canonical
 * {@code ActivityId} ({@code TrainingSession.id}) — see ADR-002 addendum.
 *
 * <p>Resolution path: {@code AthleteId} (UUID) is resolved to this athlete's
 * Strava {@link IntegrationAccount}, if one is connected. Its
 * {@code externalUserId} is the legacy Strava athlete id, used to query
 * {@code collector-strava}'s own {@code Activity} rows. Each row is then
 * translated back to its canonical {@code ActivityId} via
 * {@link ActivityReferenceRepository}, never exposing a Strava id to the
 * caller.
 */
@ApplicationScoped
public class StravaActivityQueryPort implements ActivityQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityReferenceRepository activityReferenceRepository;
    private final ActivityRepository activityRepository;

    @Inject
    public StravaActivityQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityReferenceRepository activityReferenceRepository,
            ActivityRepository activityRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityReferenceRepository = activityReferenceRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public List<Activity> listByAthlete(
            UUID athleteId,
            String sportType,
            Instant from,
            Instant to,
            int offset,
            int limit) {
        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            // This athlete has no connected Strava account; this adapter has
            // nothing to contribute. Returning empty is correct — it is not
            // an error condition, it is this source simply not applying.
            return List.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        List<com.zensyra.collector.strava.activity.Activity> stravaActivities =
                activityRepository.findPagedByAthleteId(stravaAthleteId, sportType, from, to, offset, limit);

        List<Activity> result = new ArrayList<>(stravaActivities.size());
        for (com.zensyra.collector.strava.activity.Activity stravaActivity : stravaActivities) {
            UUID canonicalActivityId = resolveCanonicalActivityId(account.get().getId(), stravaActivity);
            if (canonicalActivityId == null) {
                // An activity exists in collector-strava's own tables but has
                // no canonical ActivityReference yet. This should not happen
                // for any activity synced through ActivityUpsertService, which
                // always resolves a reference — but if it does, skipping it
                // here is safer than exposing a null canonical id to the API.
                continue;
            }
            result.add(toReadModel(canonicalActivityId, stravaActivity));
        }
        return result;
    }

    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        // Deliberately not optimized with a more specific repository method
        // (e.g. findByAthleteIdAndSource). This fetches every connected
        // account for the athlete and filters in memory for STRAVA, which is
        // wasteful in theory once an athlete has many connected sources, but
        // today every athlete has at most one (Strava is the only source).
        // Revisit only if profiling shows this actually matters — see
        // discussion in Issue A follow-up notes.
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private UUID resolveCanonicalActivityId(
            UUID integrationAccountId, com.zensyra.collector.strava.activity.Activity stravaActivity) {
        return activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(
                        integrationAccountId, stravaActivity.getStravaId().toString())
                .map(ActivityReference::getTrainingSessionId)
                .orElse(null);
    }

    private Activity toReadModel(
            UUID canonicalActivityId, com.zensyra.collector.strava.activity.Activity stravaActivity) {
        return new Activity(
                canonicalActivityId,
                stravaActivity.getName(),
                stravaActivity.getSportType(),
                stravaActivity.getDistance() != null ? stravaActivity.getDistance().doubleValue() : null,
                stravaActivity.getMovingTime(),
                stravaActivity.getStartDate(),
                stravaActivity.getTotalElevationGain() != null
                        ? stravaActivity.getTotalElevationGain().doubleValue() : null,
                stravaActivity.getAverageHeartrate() != null
                        ? stravaActivity.getAverageHeartrate().doubleValue() : null,
                stravaActivity.getAverageWatts() != null
                        ? stravaActivity.getAverageWatts().doubleValue() : null
        );
    }
}
