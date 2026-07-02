package com.zensyra.collector.strava.activitymetrics;

import com.zensyra.collector.core.identity.AthleteProfile;
import com.zensyra.collector.core.identity.AthleteProfileRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.identity.StravaActivityIdentityService;
import com.zensyra.collector.strava.stream.ActivityStreamRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@ApplicationScoped
public class ActivityMetricsService {

    private static final Logger LOG = Logger.getLogger(ActivityMetricsService.class);
    private static final int ROLLING_WINDOW_SECONDS = 30;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityStreamRepository streamRepository;

    @Inject
    ActivityMetricsRepository metricsRepository;

    @Inject
    StravaActivityIdentityService activityIdentityService;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    AthleteProfileRepository athleteProfileRepository;

    @Transactional
    public void computeAndUpsert(Long athleteId) {
        List<Activity> activities = activityRepository.findActivitiesNeedingMetricsComputation(athleteId);
        LOG.infof("ActivityMetricsService: processing %d activities for athlete %d", activities.size(), athleteId);

        // FTP is a per-athlete value; resolve it once for the whole batch. Null when
        // the athlete has no power data set (#28) — intensity factor stays null then.
        Integer ftpWatts = resolveFtpWatts(athleteId);

        for (Activity activity : activities) {
            try {
                computeForActivity(activity, ftpWatts);
            } catch (Exception e) {
                LOG.errorf(e, "Error calculating metrics for activity id=%d", activity.getId());
            }
        }
    }

    private void computeForActivity(Activity activity, Integer ftpWatts) {
        List<Integer> watts = streamRepository.findWattsByActivityIdOrdered(activity.getId());
        if (watts.size() < ROLLING_WINDOW_SECONDS) {
            return;
        }

        activityIdentityService.resolveOrCreateReference(
                activity.getAthleteId(),
                activity.getStravaId());

        double normalizedPower = computeNormalizedPower(watts);
        double averagePower = activity.getAverageWatts().doubleValue();

        ActivityMetrics metrics = metricsRepository.findByIdOptional(activity.getId())
                .orElseGet(() -> {
                    ActivityMetrics m = new ActivityMetrics();
                    m.setActivityId(activity.getId());
                    return m;
                });

        metrics.setNormalizedPower(round(normalizedPower));
        metrics.setVariabilityIndex(round(normalizedPower / averagePower));

        if (activity.getAverageHeartrate() != null && activity.getAverageHeartrate().doubleValue() > 0) {
            metrics.setEfficiencyFactor(round(normalizedPower / activity.getAverageHeartrate().doubleValue()));
        }

        // Intensity Factor = NP / FTP. Only when a positive FTP is known for the athlete;
        // otherwise it stays null (never 0, never a fallback) — see #28/#29.
        if (ftpWatts != null && ftpWatts > 0) {
            metrics.setIntensityFactor(round(normalizedPower / ftpWatts));
        }

        metricsRepository.persist(metrics);
    }

    /**
     * Resolves the athlete's FTP (watts) from the canonical {@link AthleteProfile},
     * mapping the Strava numeric athlete id through its {@link IntegrationAccount}.
     * Returns null when the account, profile, or FTP value is absent — the intensity
     * factor is then left null rather than defaulted.
     */
    private Integer resolveFtpWatts(Long stravaAthleteId) {
        IntegrationAccount account = integrationAccountRepository
                .findBySourceAndExternalUserId(IntegrationSource.STRAVA, String.valueOf(stravaAthleteId))
                .orElse(null);
        if (account == null) {
            return null;
        }
        return athleteProfileRepository.findByIdOptional(account.getAthleteId())
                .map(AthleteProfile::getFtpWatts)
                .orElse(null);
    }

    /**
     * Normalized Power (NP):
     * 1. Compute 30-second trailing rolling average of watts at each second.
     * 2. Raise each value to the 4th power.
     * 3. Average all 4th-power values.
     * 4. Take the 4th root.
     */
    private double computeNormalizedPower(List<Integer> watts) {
        int n = watts.size();
        double sumOfFourthPowers = 0.0;
        int count = n - ROLLING_WINDOW_SECONDS + 1;

        double windowSum = 0.0;
        for (int j = 0; j < ROLLING_WINDOW_SECONDS; j++) {
            windowSum += watts.get(j);
        }
        double avg = windowSum / ROLLING_WINDOW_SECONDS;
        sumOfFourthPowers += avg * avg * avg * avg;

        for (int i = ROLLING_WINDOW_SECONDS; i < n; i++) {
            windowSum += watts.get(i) - watts.get(i - ROLLING_WINDOW_SECONDS);
            avg = windowSum / ROLLING_WINDOW_SECONDS;
            sumOfFourthPowers += avg * avg * avg * avg;
        }

        double meanOfFourthPowers = sumOfFourthPowers / count;
        return Math.pow(meanOfFourthPowers, 0.25);
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).round(new MathContext(6, RoundingMode.HALF_UP));
    }
}
