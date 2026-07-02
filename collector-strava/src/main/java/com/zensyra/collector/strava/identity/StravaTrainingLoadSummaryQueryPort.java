package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.model.TrainingLoadSummary;
import com.zensyra.collector.query.port.TrainingLoadSummaryQueryPort;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoad;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strava implementation of {@link TrainingLoadSummaryQueryPort}.
 *
 * <p>Resolution path mirrors {@link StravaTrainingLoadQueryPort}: canonical
 * athleteId → Strava {@link IntegrationAccount} → stravaAthleteId → rows
 * from {@code athlete_training_load}.
 *
 * <p>Aggregation is performed in Java after loading the bounded date range.
 * Period boundaries follow ISO 8601: weeks start on Monday, months on the 1st.
 * Each period bucket carries:
 * <ul>
 *   <li>{@code totalTss} — sum of {@code tss_day} for all days in the period
 *   <li>{@code ctlEnd / atlEnd / tsbEnd} — snapshot of the last day with data
 *       in the period (approximates end-of-period fitness / fatigue / form)
 * </ul>
 *
 * <p>TSS: {@code (moving_time_s / 3600) × IF² × 100}, using each activity's real
 * intensity factor when available, falling back to IF = 0.75 only when it is null.
 * See {@code TrainingLoadService} for the full derivation.
 */
@ApplicationScoped
public class StravaTrainingLoadSummaryQueryPort implements TrainingLoadSummaryQueryPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final AthleteTrainingLoadRepository athleteTrainingLoadRepository;

    @Inject
    public StravaTrainingLoadSummaryQueryPort(
            IntegrationAccountRepository integrationAccountRepository,
            AthleteTrainingLoadRepository athleteTrainingLoadRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.athleteTrainingLoadRepository = athleteTrainingLoadRepository;
    }

    @Override
    public List<TrainingLoadSummary> listByAthlete(
            UUID athleteId, LocalDate from, LocalDate to, Granularity granularity) {

        Optional<IntegrationAccount> account = resolveStravaAccount(athleteId);
        if (account.isEmpty()) {
            return List.of();
        }

        Long stravaAthleteId = Long.parseLong(account.get().getExternalUserId());
        List<AthleteTrainingLoad> rows =
                athleteTrainingLoadRepository.findByAthleteIdAndDateRange(stravaAthleteId, from, to);

        return aggregate(athleteId, rows, granularity);
    }

    private Optional<IntegrationAccount> resolveStravaAccount(UUID athleteId) {
        return integrationAccountRepository.findByAthleteId(athleteId).stream()
                .filter(a -> a.getSource() == IntegrationSource.STRAVA)
                .findFirst();
    }

    private List<TrainingLoadSummary> aggregate(
            UUID athleteId, List<AthleteTrainingLoad> rows, Granularity granularity) {

        // TreeMap keeps period buckets in ascending date order.
        Map<LocalDate, List<AthleteTrainingLoad>> buckets = new TreeMap<>();
        for (AthleteTrainingLoad row : rows) {
            LocalDate periodStart = periodStart(row.getDate(), granularity);
            buckets.computeIfAbsent(periodStart, k -> new ArrayList<>()).add(row);
        }

        List<TrainingLoadSummary> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AthleteTrainingLoad>> entry : buckets.entrySet()) {
            LocalDate periodStart = entry.getKey();
            List<AthleteTrainingLoad> periodRows = entry.getValue();

            double totalTss = periodRows.stream()
                    .mapToDouble(r -> r.getTssDay() != null ? r.getTssDay() : 0.0)
                    .sum();
            // Last row (list is ordered by date asc) holds end-of-period snapshots.
            AthleteTrainingLoad last = periodRows.get(periodRows.size() - 1);

            result.add(new TrainingLoadSummary(
                    athleteId,
                    periodStart,
                    granularity,
                    totalTss,
                    last.getCtl(),
                    last.getAtl(),
                    last.getTsb()
            ));
        }
        return result;
    }

    private LocalDate periodStart(LocalDate date, Granularity granularity) {
        return granularity == Granularity.WEEKLY
                ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : date.withDayOfMonth(1);
    }
}
