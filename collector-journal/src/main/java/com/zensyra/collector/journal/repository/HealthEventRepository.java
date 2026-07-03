package com.zensyra.collector.journal.repository;

import com.zensyra.collector.journal.model.HealthEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HealthEventRepository implements PanacheRepositoryBase<HealthEvent, UUID> {

    /**
     * Returns every event that overlaps the closed window {@code [from, to]}.
     * An event with a null {@code endDate} is treated as ongoing (open-ended),
     * so the overlap condition is {@code startDate <= to AND (endDate IS NULL
     * OR endDate >= from)} — this correctly includes events that span the whole
     * window and events still in progress.
     */
    public List<HealthEvent> findByAthleteIdAndDateRange(UUID athleteId, LocalDate from, LocalDate to) {
        return find("athleteId = ?1 and startDate <= ?3 and (endDate is null or endDate >= ?2) "
                + "order by startDate asc", athleteId, from, to).list();
    }
}
