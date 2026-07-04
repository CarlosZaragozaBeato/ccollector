package com.zensyra.collector.journal.service;

import com.zensyra.collector.journal.model.SubjectiveState;
import com.zensyra.collector.journal.model.TrainingDay;
import com.zensyra.collector.journal.repository.TrainingDayRepository;
import com.zensyra.collector.query.model.TrainingDaySummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Optional;
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
        // (athleteId, date) is the real unique key. The entity's id is
        // pre-initialized (UUID.randomUUID()), so it is never null and cannot be
        // used to tell create from update — deriving isNew from the lookup itself
        // is the source of truth. Using getId() here silently skipped persist()
        // for new entries (create data loss).
        Optional<TrainingDay> existing = repository.findByAthleteIdAndDate(athleteId, date);
        TrainingDay td = existing.orElseGet(TrainingDay::new);

        boolean isNew = existing.isEmpty();
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
