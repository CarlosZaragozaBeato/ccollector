package com.zensyra.collector.strava.activitymetrics;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ActivityMetricsRepository implements PanacheRepositoryBase<ActivityMetrics, Long> {

    public Optional<ActivityMetrics> findByActivityId(Long activityId) {
        return findByIdOptional(activityId);
    }

    /**
     * Metrics rows for the athlete that have normalized power computed but no
     * intensity factor yet — the historical rows created before the athlete's FTP
     * existed (#29 flag). This is a separate, additive query: it never affects the
     * normal ingestion path, which skips activities that already have a row.
     */
    public List<ActivityMetrics> findMissingIntensityFactorByAthlete(Long athleteStravaId) {
        return find(
                "normalizedPower is not null and intensityFactor is null "
                        + "and activityId in "
                        + "(select a.id from Activity a where a.athleteId = ?1)",
                athleteStravaId).list();
    }
}
