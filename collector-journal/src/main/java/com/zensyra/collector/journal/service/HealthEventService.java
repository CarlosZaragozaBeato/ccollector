package com.zensyra.collector.journal.service;

import com.zensyra.collector.journal.model.HealthEvent;
import com.zensyra.collector.journal.model.HealthEventType;
import com.zensyra.collector.journal.repository.HealthEventRepository;
import com.zensyra.collector.query.model.HealthEventSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class HealthEventService {

    private final HealthEventRepository repository;

    @Inject
    public HealthEventService(HealthEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new health event. Unlike {@code TrainingDay}, HealthEvent has no
     * natural key (multiple events may start on the same date), so this always
     * inserts — there is no upsert and no update endpoint in scope.
     */
    @Transactional
    public HealthEventSummary create(UUID athleteId, LocalDate startDate, LocalDate endDate,
                                     String typeStr, String title, String notes) {
        HealthEvent event = new HealthEvent();
        event.setAthleteId(athleteId);
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        event.setType(HealthEventType.valueOf(typeStr));
        event.setTitle(title);
        event.setNotes(notes);

        repository.persist(event);

        return toSummary(event);
    }

    private HealthEventSummary toSummary(HealthEvent event) {
        return new HealthEventSummary(
                event.getId(),
                event.getStartDate(),
                event.getEndDate(),
                event.getType().name(),
                event.getTitle(),
                event.getNotes(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
