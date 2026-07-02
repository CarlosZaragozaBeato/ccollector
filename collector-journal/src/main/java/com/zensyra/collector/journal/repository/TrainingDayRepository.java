package com.zensyra.collector.journal.repository;

import com.zensyra.collector.journal.model.TrainingDay;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrainingDayRepository implements PanacheRepositoryBase<TrainingDay, UUID> {

    public Optional<TrainingDay> findByAthleteIdAndDate(UUID athleteId, LocalDate date) {
        return find("athleteId = ?1 and date = ?2", athleteId, date).firstResultOptional();
    }

    public List<TrainingDay> findByAthleteIdAndDateRange(UUID athleteId, LocalDate from, LocalDate to) {
        return find("athleteId = ?1 and date >= ?2 and date <= ?3 order by date asc",
                athleteId, from, to).list();
    }
}
