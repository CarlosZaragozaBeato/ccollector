package com.zensyra.collector.suunto.trainingload;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.port.TrainingStressContributionPort;
import com.zensyra.collector.suunto.workout.SuuntoWorkout;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Suunto implementation of {@link TrainingStressContributionPort}.
 *
 * <p>Resolution path: canonical UUID → Suunto {@link IntegrationAccount}
 * → {@code suuntoUser} string → {@code suunto_workouts} rows in date range
 * → per-day TSS sum. Rows where {@code tss IS NULL} (no qualified tssList
 * entry) contribute nothing for that workout — null is not treated as zero.
 * The TSS value is what the Suunto API reports directly; it is not
 * recomputed here.
 */
@ApplicationScoped
public class SuuntoTrainingStressContributionPort implements TrainingStressContributionPort {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final SuuntoWorkoutRepository workoutRepository;

    @Inject
    public SuuntoTrainingStressContributionPort(
            IntegrationAccountRepository integrationAccountRepository,
            SuuntoWorkoutRepository workoutRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.workoutRepository = workoutRepository;
    }

    @Override
    public Map<LocalDate, Double> contributionsForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        Optional<IntegrationAccount> account = integrationAccountRepository
                .findByAthleteId(athleteId).stream()
                .filter(a -> a.getSource() == IntegrationSource.SUUNTO)
                .findFirst();
        if (account.isEmpty()) {
            return Map.of();
        }

        String suuntoUser = account.get().getExternalUserId();
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<SuuntoWorkout> workouts =
                workoutRepository.findByUserAndDateRange(suuntoUser, fromInstant, toInstant);

        return workouts.stream()
                .filter(w -> w.getTss() != null)
                .collect(Collectors.groupingBy(
                        w -> w.getStartDate().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.summingDouble(SuuntoWorkout::getTss)
                ));
    }
}
