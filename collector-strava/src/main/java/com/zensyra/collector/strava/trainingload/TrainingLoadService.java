package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetrics;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Computes daily training load metrics (CTL, ATL, TSB) per athlete using
 * exponential moving averages over estimated TSS.
 *
 * Estimated TSS: (moving_time_seconds / 3600) × IF² × 100. IF is the real
 * per-activity intensity factor (NP / FTP) from {@code activity_metrics};
 * when that is null (no power meter, or FTP unavailable) it falls back to the
 * {@link #FALLBACK_INTENSITY_FACTOR} approximation.
 * CTL (fitness): 42-day EMA — α = 1/42.
 * ATL (fatigue): 7-day EMA — α = 1/7.
 * TSB (form):     CTL − ATL
 */
@ApplicationScoped
public class TrainingLoadService {

    private static final Logger LOG = Logger.getLogger(TrainingLoadService.class);

    /** Used only when an activity has no real intensity factor available. */
    static final double FALLBACK_INTENSITY_FACTOR = 0.75;
    static final int CTL_DAYS = 42;
    static final int ATL_DAYS = 7;
    static final int WINDOW_DAYS = 90;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    AthleteTrainingLoadRepository trainingLoadRepository;

    @Inject
    ActivityMetricsRepository metricsRepository;

    @Transactional
    public void computeAndUpsert(Long athleteId, LocalDate targetDate) {
        LocalDate windowStart = targetDate.minusDays(WINDOW_DAYS - 1);

        Instant from = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Activity> activities = activityRepository.findByAthleteIdAndDateRange(athleteId, from, to);

        Map<LocalDate, Double> dailyTss = activities.stream()
                .filter(a -> a.getMovingTime() != null && a.getMovingTime() > 0)
                .collect(Collectors.groupingBy(
                        a -> a.getStartDate().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.summingDouble(this::estimateTss)
                ));

        double alphaCtl = 1.0 / CTL_DAYS;
        double alphaAtl = 1.0 / ATL_DAYS;
        double ctl = 0.0;
        double atl = 0.0;

        for (int i = WINDOW_DAYS - 1; i >= 0; i--) {
            LocalDate day = targetDate.minusDays(i);
            double tss = dailyTss.getOrDefault(day, 0.0);
            ctl = ctl * (1.0 - alphaCtl) + tss * alphaCtl;
            atl = atl * (1.0 - alphaAtl) + tss * alphaAtl;
        }

        double tsb = ctl - atl;
        double tssDay = dailyTss.getOrDefault(targetDate, 0.0);

        upsert(athleteId, targetDate, tssDay, ctl, atl, tsb);

        LOG.infof("TrainingLoad upserted — athleteId=%d date=%s tss=%.1f ctl=%.1f atl=%.1f tsb=%.1f",
                athleteId, targetDate, tssDay, ctl, atl, tsb);
    }

    /**
     * Recomputes CTL/ATL/TSB for every date that <em>already</em> has a stored
     * {@code athlete_training_load} row for this athlete, so historical values
     * reflect the real intensity factor now available. Deliberately does not
     * create rows for dates without an existing row — it only corrects what is
     * there. The normal per-sync flow is untouched.
     *
     * @return the number of existing daily rows recomputed
     */
    @Transactional
    public int backfill(Long athleteId) {
        List<LocalDate> dates = trainingLoadRepository.findDatesByAthleteId(athleteId);
        for (LocalDate date : dates) {
            computeAndUpsert(athleteId, date);
        }
        LOG.infof("TrainingLoad backfill — athleteId=%d recomputed %d existing daily rows",
                athleteId, dates.size());
        return dates.size();
    }

    /**
     * Estimated TSS for a single activity: {@code hours × IF² × 100}. Uses the
     * activity's real intensity factor from {@code activity_metrics} when present,
     * falling back to {@link #FALLBACK_INTENSITY_FACTOR} only when it is genuinely
     * null (no power meter, or FTP not yet available for the athlete).
     */
    double estimateTss(Activity activity) {
        double hours = activity.getMovingTime() / 3600.0;
        double intensityFactor = resolveIntensityFactor(activity.getId());
        return hours * intensityFactor * intensityFactor * 100.0;
    }

    private double resolveIntensityFactor(Long activityId) {
        // Optional.map yields empty when intensityFactor is null, so the fallback
        // applies both when there is no metrics row and when IF was never computed.
        return metricsRepository.findByActivityId(activityId)
                .map(ActivityMetrics::getIntensityFactor)
                .map(BigDecimal::doubleValue)
                .orElse(FALLBACK_INTENSITY_FACTOR);
    }

    private void upsert(Long athleteId, LocalDate date, double tssDay, double ctl, double atl, double tsb) {
        Optional<AthleteTrainingLoad> existing = trainingLoadRepository.findByAthleteAndDate(athleteId, date);
        AthleteTrainingLoad record = existing.orElseGet(AthleteTrainingLoad::new);

        record.setAthleteId(athleteId);
        record.setDate(date);
        record.setTssDay(tssDay);
        record.setCtl(ctl);
        record.setAtl(atl);
        record.setTsb(tsb);

        if (existing.isEmpty()) {
            trainingLoadRepository.persist(record);
        }
    }
}
