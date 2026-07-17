package com.zensyra.collector.strava.trainingload;

import com.zensyra.collector.query.composer.DailyTssComposer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes daily training load metrics (CTL, ATL, TSB) per athlete using
 * exponential moving averages over estimated TSS.
 *
 * <p>TSS is now sourced from {@link DailyTssComposer}, which aggregates
 * contributions from all registered {@code TrainingStressContributionPort}
 * implementations via {@code Double::sum}. Currently only
 * {@link StravaTrainingStressContributionPort} is registered; the formula
 * and numerical output are identical to the previous inline computation.
 *
 * <p>CTL (fitness): 42-day EMA — α = 1/42.
 * ATL (fatigue):  7-day EMA — α = 1/7.
 * TSB (form):     CTL − ATL.
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
    AthleteTrainingLoadRepository trainingLoadRepository;

    @Inject
    DailyTssComposer dailyTssComposer;

    @Transactional
    public void computeAndUpsert(UUID athleteId, LocalDate targetDate) {
        LocalDate windowStart = targetDate.minusDays(WINDOW_DAYS - 1);

        Map<LocalDate, Double> dailyTss =
                dailyTssComposer.aggregate(athleteId, windowStart, targetDate);

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

        LOG.infof("TrainingLoad upserted — athleteId=%s date=%s tss=%.1f ctl=%.1f atl=%.1f tsb=%.1f",
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
    public int backfill(UUID athleteId) {
        List<LocalDate> dates = trainingLoadRepository.findDatesByAthleteId(athleteId);
        for (LocalDate date : dates) {
            computeAndUpsert(athleteId, date);
        }
        LOG.infof("TrainingLoad backfill — athleteId=%s recomputed %d existing daily rows",
                athleteId, dates.size());
        return dates.size();
    }

    private void upsert(UUID athleteId, LocalDate date, double tssDay, double ctl, double atl, double tsb) {
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
