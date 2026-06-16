package com.zensyra.collector.strava.athletestats;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class AthleteStatsSnapshotRepository implements PanacheRepositoryBase<AthleteStatsSnapshot, Long> {

    public Optional<AthleteStatsSnapshot> findByAthleteAndDate(Long athleteId, LocalDate snapshotDate) {
        return find("athleteId = ?1 and snapshotDate = ?2", athleteId, snapshotDate).firstResultOptional();
    }

    public Optional<AthleteStatsSnapshot> findLatestByAthleteId(Long athleteId) {
        return find("athleteId = ?1 order by snapshotDate desc", athleteId).firstResultOptional();
    }
}
