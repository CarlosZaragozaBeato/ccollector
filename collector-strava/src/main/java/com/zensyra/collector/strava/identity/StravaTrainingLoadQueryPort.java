package com.zensyra.collector.strava.identity;

import com.zensyra.collector.query.model.TrainingLoad;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Strava implementation of {@link TrainingLoadQueryPort}.
 *
 * <p>After migration 043 the underlying {@code athlete_training_load} table is
 * keyed by the canonical athlete UUID, so no source-specific identity
 * translation is needed — the query goes straight to the repository.
 */
@ApplicationScoped
public class StravaTrainingLoadQueryPort implements TrainingLoadQueryPort {

    private final AthleteTrainingLoadRepository athleteTrainingLoadRepository;

    @Inject
    public StravaTrainingLoadQueryPort(AthleteTrainingLoadRepository athleteTrainingLoadRepository) {
        this.athleteTrainingLoadRepository = athleteTrainingLoadRepository;
    }

    @Override
    public List<TrainingLoad> listRecentByAthlete(UUID athleteId, LocalDate from) {
        return athleteTrainingLoadRepository.findRecentByAthleteId(athleteId, from)
                .stream()
                .map(record -> toReadModel(athleteId, record))
                .toList();
    }

    private TrainingLoad toReadModel(
            UUID athleteId, com.zensyra.collector.strava.trainingload.AthleteTrainingLoad record) {
        return new TrainingLoad(
                athleteId,
                record.getDate(),
                record.getTssDay(),
                record.getCtl(),
                record.getAtl(),
                record.getTsb()
        );
    }
}
