package com.zensyra.collector.journal.repository;

import com.zensyra.collector.journal.model.RaceResult;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RaceResultRepository implements PanacheRepositoryBase<RaceResult, UUID> {

    /**
     * Returns the athlete's races whose {@code raceDate} falls within the closed
     * window {@code [from, to]}, ascending. A race is a single-date event (not an
     * interval), so this is a plain range filter — unlike HealthEvent's overlap.
     */
    public List<RaceResult> findByAthleteIdAndDateRange(UUID athleteId, LocalDate from, LocalDate to) {
        return find("athleteId = ?1 and raceDate >= ?2 and raceDate <= ?3 order by raceDate asc",
                athleteId, from, to).list();
    }
}
