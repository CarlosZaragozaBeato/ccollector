package com.zensyra.collector.journal.query;

import com.zensyra.collector.journal.model.RaceResult;
import com.zensyra.collector.journal.repository.RaceResultRepository;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.port.RaceResultQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JournalRaceResultQueryPort implements RaceResultQueryPort {

    private final RaceResultRepository repository;

    @Inject
    public JournalRaceResultQueryPort(RaceResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RaceResultSummary> findByAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return repository.findByAthleteIdAndDateRange(athleteId, from, to)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private RaceResultSummary toSummary(RaceResult race) {
        return new RaceResultSummary(
                race.getId(),
                race.getRaceDate(),
                race.getRaceName(),
                race.getDistanceMeters(),
                race.getGoalFinishTime(),
                race.getActualFinishTime(),
                race.getPosition(),
                race.getNotes(),
                race.getLinkedActivityId(),
                race.getCreatedAt(),
                race.getUpdatedAt()
        );
    }
}
