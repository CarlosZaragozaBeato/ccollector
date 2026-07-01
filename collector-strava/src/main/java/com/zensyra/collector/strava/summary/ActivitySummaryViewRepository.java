package com.zensyra.collector.strava.summary;

import com.zensyra.collector.query.model.Granularity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Runs native SQL against the TimescaleDB continuous-aggregate views
 * {@code weekly_activity_summary} and {@code monthly_activity_summary}.
 * These views are not exposed as JPA entities because they have a composite
 * primary key (period_start, athlete_id) and are read-only — a native query
 * is simpler and correct here.
 */
@ApplicationScoped
public class ActivitySummaryViewRepository {

    private final EntityManager em;

    @Inject
    public ActivitySummaryViewRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Returns per-period aggregates for the given Strava athlete id and date
     * range. {@code from} is inclusive; {@code to} is inclusive (converted to
     * exclusive upper bound internally).
     */
    @SuppressWarnings("unchecked")
    public List<ActivitySummaryRow> findByAthleteId(
            Long stravaAthleteId,
            LocalDate from,
            LocalDate to,
            Granularity granularity) {

        String viewName = granularity == Granularity.WEEKLY
                ? "weekly_activity_summary"
                : "monthly_activity_summary";
        String periodCol = granularity == Granularity.WEEKLY ? "week" : "month";

        Timestamp fromTs = Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp toTs = Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        List<Object[]> rows = em.createNativeQuery(
                "SELECT " + periodCol + ", num_activities, total_distance_m, total_moving_time_s, total_elevation_gain_m "
                        + "FROM " + viewName
                        + " WHERE athlete_id = ?1 AND " + periodCol + " >= ?2 AND " + periodCol + " < ?3 "
                        + "ORDER BY " + periodCol + " ASC"
        )
                .setParameter(1, stravaAthleteId)
                .setParameter(2, fromTs)
                .setParameter(3, toTs)
                .getResultList();

        return rows.stream().map(ActivitySummaryRow::fromRow).toList();
    }
}
