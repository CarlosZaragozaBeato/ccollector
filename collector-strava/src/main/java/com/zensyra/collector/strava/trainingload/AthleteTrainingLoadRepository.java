package com.zensyra.collector.strava.trainingload;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AthleteTrainingLoadRepository implements PanacheRepositoryBase<AthleteTrainingLoad, Long> {

    public Optional<AthleteTrainingLoad> findByAthleteAndDate(Long athleteId, LocalDate date) {
        return find("athleteId = ?1 and date = ?2", athleteId, date).firstResultOptional();
    }

    public List<AthleteTrainingLoad> findRecentByAthleteId(Long athleteId, LocalDate from) {
        return list("athleteId = ?1 and date >= ?2 order by date asc", athleteId, from);
    }

    public List<AthleteTrainingLoad> findByAthleteIdAndDateRange(Long athleteId, LocalDate from, LocalDate to) {
        return list("athleteId = ?1 and date >= ?2 and date <= ?3 order by date asc", athleteId, from, to);
    }
}
