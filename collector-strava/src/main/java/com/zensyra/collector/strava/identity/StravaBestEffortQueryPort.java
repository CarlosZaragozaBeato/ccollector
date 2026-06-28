package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.BestEffort;
import com.zensyra.collector.query.port.BestEffortQueryPort;
import com.zensyra.collector.strava.besteffort.ActivityBestEffort;
import com.zensyra.collector.strava.besteffort.ActivityBestEffortRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Strava implementation of {@link BestEffortQueryPort}.
 *
 * <p>Resolution path mirrors {@link StravaActivityQueryPort}: the canonical
 * {@code AthleteId} is resolved to this athlete's Strava
 * {@link IntegrationAccount}, whose {@code externalUserId} is used to query
 * {@code collector-strava}'s own best-effort rows. Each row's
 * {@code activityStravaId} is then translated back to its canonical
 * {@code ActivityId} via {@link com.zensyra.collector.core.identity.ActivityReferenceRepository},
 * never exposing a Strava id to the caller — and never exposing
 * {@code isKom}, which has no field on the canonical {@link BestEffort}
 * read-model at all (see that type's Javadoc for why).
 */
@ApplicationScoped
public class StravaBestEffortQueryPort implements BestEffortQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityBestEffortRepository activityBestEffortRepository;
    private final ActivityReferenceResolver activityReferenceResolver;

    @Inject
    public StravaBestEffortQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityBestEffortRepository activityBestEffortRepository,
            ActivityReferenceResolver activityReferenceResolver) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityBestEffortRepository = activityBestEffortRepository;
        this.activityReferenceResolver = activityReferenceResolver;
    }

    @Override
    public List<BestEffort> listTopByAthlete(UUID athleteId, int limit) {
        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            return List.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        List<ActivityBestEffort> stravaEfforts =
                activityBestEffortRepository.findTopPrsByAthleteId(stravaAthleteId, limit);

        List<BestEffort> result = new ArrayList<>(stravaEfforts.size());
        for (ActivityBestEffort stravaEffort : stravaEfforts) {
            UUID canonicalActivityId = activityReferenceResolver.resolveCanonicalActivityId(
                    account.get().getId(), stravaEffort.getActivityStravaId().toString());
            if (canonicalActivityId == null) {
                // Same defensive skip as StravaActivityQueryPort: a best
                // effort row referencing an activity with no canonical
                // ActivityReference should not surface a null id to callers.
                continue;
            }
            result.add(toReadModel(canonicalActivityId, stravaEffort));
        }
        return result;
    }

    // Deliberately not optimized with a more specific repository method
    // (e.g. findByAthleteIdAndSource) — see the identical note on
    // StravaActivityQueryPort.resolveStravaAccount for the reasoning.
    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(account -> account.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private BestEffort toReadModel(UUID canonicalActivityId, ActivityBestEffort stravaEffort) {
        return new BestEffort(
                canonicalActivityId,
                stravaEffort.getName(),
                stravaEffort.getDistance(),
                stravaEffort.getElapsedTime(),
                stravaEffort.getPrRank()
        );
    }
}
