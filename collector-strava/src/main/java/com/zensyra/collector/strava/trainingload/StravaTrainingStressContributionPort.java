package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.port.TrainingStressContributionPort;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Strava implementation of {@link TrainingStressContributionPort}.
 *
 * <p>Resolution path: canonical UUID → Strava {@link IntegrationAccount}
 * → numeric {@code stravaAthleteId} → activities in date range → per-day
 * TSS sum. The TSS formula is identical to what {@code TrainingLoadService}
 * previously computed inline: {@code hours × IF² × 100}, with the real
 * intensity factor from {@code activity_metrics} and a fallback of
 * {@link TrainingLoadService#FALLBACK_INTENSITY_FACTOR} when it is absent.
 */
@ApplicationScoped
public class StravaTrainingStressContributionPort implements TrainingStressContributionPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final ActivityRepository activityRepository;
    private final ActivityMetricsRepository metricsRepository;

    @Inject
    public StravaTrainingStressContributionPort(
            IntegrationAccountRepository integrationAccountRepository,
            ActivityRepository activityRepository,
            ActivityMetricsRepository metricsRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.activityRepository = activityRepository;
        this.metricsRepository = metricsRepository;
    }

    @Override
    public Map<LocalDate, Double> contributionsForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        Optional<IntegrationAccount> account = integrationAccountRepository
                .findByAthleteId(athleteId).stream()
                .filter(a -> a.getSource() == IntegrationSource.STRAVA)
                .findFirst();
        if (account.isEmpty()) {
            return Map.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Activity> activities =
                activityRepository.findByAthleteIdAndDateRange(stravaAthleteId, fromInstant, toInstant);

        return activities.stream()
                .filter(a -> a.getMovingTime() != null && a.getMovingTime() > 0)
                .collect(Collectors.groupingBy(
                        a -> a.getStartDate().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.summingDouble(this::estimateTss)
                ));
    }

    private double estimateTss(Activity activity) {
        double hours = activity.getMovingTime() / 3600.0;
        double intensityFactor = resolveIntensityFactor(activity.getId());
        return hours * intensityFactor * intensityFactor * 100.0;
    }

    private double resolveIntensityFactor(Long activityId) {
        return metricsRepository.findByActivityId(activityId)
                .map(ActivityMetrics::getIntensityFactor)
                .map(BigDecimal::doubleValue)
                .orElse(TrainingLoadService.FALLBACK_INTENSITY_FACTOR);
    }
}
