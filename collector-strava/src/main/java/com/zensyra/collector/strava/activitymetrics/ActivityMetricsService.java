package com.zensyra.collector.strava.activitymetrics;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
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

    @Transactional
    public void computeAndUpsert(Long athleteId) {
        List<Activity> activities = activityRepository.findActivitiesNeedingMetricsComputation(athleteId);
        LOG.infof("ActivityMetricsService: procesando %d actividades para atleta %d", activities.size(), athleteId);

        for (Activity activity : activities) {
            try {
                computeForActivity(activity);
            } catch (Exception e) {
                LOG.errorf(e, "Error calculando métricas para actividad id=%d", activity.getId());
            }
        }
    }

    private void computeForActivity(Activity activity) {
        List<Integer> watts = streamRepository.findWattsByActivityIdOrdered(activity.getId());
        if (watts.size() < ROLLING_WINDOW_SECONDS) {
            return;
        }

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

        metricsRepository.persist(metrics);
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
