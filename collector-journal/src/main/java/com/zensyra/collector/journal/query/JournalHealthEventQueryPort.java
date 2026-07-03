package com.zensyra.collector.journal.query;

import com.zensyra.collector.journal.model.HealthEvent;
import com.zensyra.collector.journal.repository.HealthEventRepository;
import com.zensyra.collector.query.model.HealthEventSummary;
import com.zensyra.collector.query.port.HealthEventQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JournalHealthEventQueryPort implements HealthEventQueryPort {

    private final HealthEventRepository repository;

    @Inject
    public JournalHealthEventQueryPort(HealthEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<HealthEventSummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return repository.findByAthleteIdAndDateRange(athleteId, from, to)
                .stream()
                .map(this::toSummary)
                .toList();
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
