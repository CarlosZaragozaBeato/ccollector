package com.zensyra.collector.journal.query;

import com.zensyra.collector.journal.model.TrainingDay;
import com.zensyra.collector.journal.repository.TrainingDayRepository;
import com.zensyra.collector.query.model.TrainingDaySummary;
import com.zensyra.collector.query.port.TrainingDayQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JournalTrainingDayQueryPort implements TrainingDayQueryPort {

    private final TrainingDayRepository repository;

    @Inject
    public JournalTrainingDayQueryPort(TrainingDayRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TrainingDaySummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return repository.findByAthleteIdAndDateRange(athleteId, from, to)
                .stream()
                .map(this::toSummary)
                .toList();
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
