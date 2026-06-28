package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

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
 * Estimated TSS: (moving_time_seconds / 3600) × IF² × 100, with IF = 0.75.
 * CTL (fitness): 42-day EMA — α = 1/42.
 * ATL (fatigue): 7-day EMA — α = 1/7.
 * TSB (form):     CTL − ATL
 */
@ApplicationScoped
public class TrainingLoadService {

    private static final Logger LOG = Logger.getLogger(TrainingLoadService.class);

    static final double INTENSITY_FACTOR = 0.75;
    static final int CTL_DAYS = 42;
    static final int ATL_DAYS = 7;
    static final int WINDOW_DAYS = 90;

    @Inject
    ActivityRepository activityRepository;

    @Inject
    AthleteTrainingLoadRepository trainingLoadRepository;

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
                        Collectors.summingDouble(a -> estimateTss(a.getMovingTime()))
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

    static double estimateTss(int movingTimeSeconds) {
        double hours = movingTimeSeconds / 3600.0;
        return hours * INTENSITY_FACTOR * INTENSITY_FACTOR * 100.0;
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
