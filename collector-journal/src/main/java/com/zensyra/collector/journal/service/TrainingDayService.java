package com.zensyra.collector.journal.service;

import com.zensyra.collector.journal.model.SubjectiveState;
import com.zensyra.collector.journal.model.TrainingDay;
import com.zensyra.collector.journal.repository.TrainingDayRepository;
import com.zensyra.collector.query.model.TrainingDaySummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class TrainingDayService {

    private final TrainingDayRepository repository;

    @Inject
    public TrainingDayService(TrainingDayRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TrainingDaySummary upsert(UUID athleteId, LocalDate date,
                                     Integer perceivedEffort, String subjectiveStateStr,
                                     String notes, Double weightKg) {
        TrainingDay td = repository.findByAthleteIdAndDate(athleteId, date)
                .orElseGet(TrainingDay::new);

        boolean isNew = td.getId() == null;
        if (isNew) {
            td.setAthleteId(athleteId);
            td.setDate(date);
        }

        td.setPerceivedEffort(perceivedEffort);
        td.setSubjectiveState(subjectiveStateStr != null ? SubjectiveState.valueOf(subjectiveStateStr) : null);
        td.setNotes(notes);
        td.setWeightKg(weightKg);

        if (isNew) {
            repository.persist(td);
        }

        return toSummary(td);
    }

    private TrainingDaySummary toSummary(TrainingDay td) {
        return new TrainingDaySummary(
                td.getDate(),
                td.getPerceivedEffort(),
                td.getSubjectiveState() != null ? td.getSubjectiveState().name() : null,
                td.getNotes(),
                td.getWeightKg(),
                td.getCreatedAt(),
                td.getUpdatedAt()
        );
    }
}
