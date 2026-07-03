package com.zensyra.collector.journal.service;

import com.zensyra.collector.journal.model.RaceResult;
import com.zensyra.collector.journal.repository.RaceResultRepository;
import com.zensyra.collector.query.model.RaceResultSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class RaceResultService {

    private final RaceResultRepository repository;

    @Inject
    public RaceResultService(RaceResultRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new race result. Like HealthEvent, RaceResult has no natural key
     * (an athlete may race more than once on the same date), so this always
     * inserts — there is no upsert and no update endpoint in scope.
     */
    @Transactional
    public RaceResultSummary create(UUID athleteId, LocalDate raceDate, String raceName,
                                    Double distanceMeters, Integer goalFinishTime,
                                    Integer actualFinishTime, Integer position,
                                    String notes, UUID linkedActivityId) {
        RaceResult race = new RaceResult();
        race.setAthleteId(athleteId);
        race.setRaceDate(raceDate);
        race.setRaceName(raceName);
        race.setDistanceMeters(distanceMeters);
        race.setGoalFinishTime(goalFinishTime);
        race.setActualFinishTime(actualFinishTime);
        race.setPosition(position);
        race.setNotes(notes);
        race.setLinkedActivityId(linkedActivityId);

        repository.persist(race);

        return toSummary(race);
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
