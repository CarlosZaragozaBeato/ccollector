package com.zensyra.collector.query.composer;

import com.zensyra.collector.query.model.RacePerformanceContext;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.query.model.TrainingLoadPoint;
import com.zensyra.collector.query.port.RaceResultQueryPort;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes each {@link RaceResultSummary}'s preceding training-load context —
 * CTL/ATL/TSB on race day, 7 days before, and 42 days before — by composing the
 * provider-neutral {@link RaceResultQueryPort} (races, from the journal) and
 * {@link TrainingLoadQueryPort} (daily load snapshots, from the training-load
 * model). Written as the general N-source case per the
 * {@link ActivityQueryComposer} precedent, even though one adapter of each is
 * registered today.
 *
 * <p>This lives in {@code collector-query} rather than in a source module
 * because it is a <em>two-module composition</em>: no single source can produce
 * it, and neither source module may depend on the other (see ADR-001, the
 * source-agnostic separation). Identity bridging (canonical {@code UUID} ↔ the
 * Strava numeric id behind {@code athlete_training_load}) stays encapsulated in
 * the training-load port implementation; this composer works purely in
 * {@code UUID}.
 *
 * <p><strong>Look-back windows.</strong> 7 and 42 days are the standard PMC
 * pairing — 42 matches the CTL EMA time constant (α = 1/42) and 7 the ATL time
 * constant (α = 1/7), as computed by {@code TrainingLoadService} and documented
 * in {@code docs/design/003 §2.2}. The equivalent integers there
 * ({@code CTL_DAYS}/{@code ATL_DAYS}) are package-private in {@code
 * collector-strava} and mean the EMA decay rather than a sampling offset, so
 * they cannot be reused across the module boundary; these constants are declared
 * fresh here, mirroring that pairing. (An 84-day build-phase window is a
 * candidate for a future issue; not implemented here.)
 */
@ApplicationScoped
public class RacePerformanceComposer {

    /** Short-term fatigue look-back — mirrors the ATL time constant (α = 1/7). */
    static final int SHORT_TERM_LOOKBACK_DAYS = 7;
    /** Chronic fitness look-back — mirrors the CTL time constant (α = 1/42). */
    static final int CHRONIC_LOOKBACK_DAYS = 42;
    /**
     * How far from a target date the nearest daily row may be and still be used.
     * Rows exist mainly on activity days, so short rest gaps are normal; CTL
     * barely moves over 3 days and ATL's drift stays acceptable. Beyond this the
     * point is reported as "insufficient data" rather than approximated.
     */
    static final int NEAREST_DAY_TOLERANCE_DAYS = 3;

    private final Instance<RaceResultQueryPort> raceResultPorts;
    private final Instance<TrainingLoadQueryPort> trainingLoadPorts;

    @Inject
    public RacePerformanceComposer(
            Instance<RaceResultQueryPort> raceResultPorts,
            Instance<TrainingLoadQueryPort> trainingLoadPorts) {
        this.raceResultPorts = raceResultPorts;
        this.trainingLoadPorts = trainingLoadPorts;
    }

    /**
     * Returns the training-load context for every race in {@code [from, to]} for
     * the athlete, newest race first. All training load is fetched once over a
     * single bounded date range and sampled in memory — never one lookup per race
     * (see the N+1 warning in {@code docs/design/003 §2.3}).
     */
    public List<RacePerformanceContext> composeForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        List<RaceResultSummary> races = new ArrayList<>();
        for (RaceResultQueryPort port : raceResultPorts) {
            races.addAll(port.findByAthlete(athleteId, from, to));
        }
        if (races.isEmpty()) {
            return List.of();
        }

        LocalDate earliestRace = races.stream()
                .map(RaceResultSummary::raceDate)
                .min(Comparator.naturalOrder())
                .orElseThrow(); // non-empty, checked above
        LocalDate loadFrom = earliestRace.minusDays(CHRONIC_LOOKBACK_DAYS + NEAREST_DAY_TOLERANCE_DAYS);

        Map<LocalDate, TrainingLoad> loadByDate = new HashMap<>();
        for (TrainingLoadQueryPort port : trainingLoadPorts) {
            for (TrainingLoad load : port.listRecentByAthlete(athleteId, loadFrom)) {
                // One row per (athlete, date); if two sources ever collide on a
                // date, the first registered source wins deterministically.
                loadByDate.putIfAbsent(load.date(), load);
            }
        }

        return races.stream()
                .sorted(Comparator.comparing(RaceResultSummary::raceDate).reversed()
                        .thenComparing(RaceResultSummary::id))
                .map(race -> toContext(race, loadByDate))
                .toList();
    }

    private RacePerformanceContext toContext(RaceResultSummary race, Map<LocalDate, TrainingLoad> loadByDate) {
        LocalDate raceDate = race.raceDate();
        return new RacePerformanceContext(
                race,
                sampleAt(loadByDate, raceDate),
                sampleAt(loadByDate, raceDate.minusDays(SHORT_TERM_LOOKBACK_DAYS)),
                sampleAt(loadByDate, raceDate.minusDays(CHRONIC_LOOKBACK_DAYS))
        );
    }

    /**
     * Nearest daily row within ±{@link #NEAREST_DAY_TOLERANCE_DAYS} of
     * {@code target}, preferring the earlier date on a tie (never leaks a future
     * row when an equidistant earlier one exists). Returns an explicit
     * "insufficient data" point when nothing is within tolerance.
     */
    private TrainingLoadPoint sampleAt(Map<LocalDate, TrainingLoad> loadByDate, LocalDate target) {
        for (int distance = 0; distance <= NEAREST_DAY_TOLERANCE_DAYS; distance++) {
            TrainingLoad earlier = loadByDate.get(target.minusDays(distance));
            if (earlier != null) {
                return availablePoint(target, earlier);
            }
            if (distance > 0) {
                TrainingLoad later = loadByDate.get(target.plusDays(distance));
                if (later != null) {
                    return availablePoint(target, later);
                }
            }
        }
        return new TrainingLoadPoint(target, null, null, null, null, false);
    }

    private TrainingLoadPoint availablePoint(LocalDate requested, TrainingLoad load) {
        return new TrainingLoadPoint(requested, load.date(), load.ctl(), load.atl(), load.tsb(), true);
    }
}
