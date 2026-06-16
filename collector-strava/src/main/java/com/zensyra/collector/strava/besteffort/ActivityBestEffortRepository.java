package com.zensyra.collector.strava.besteffort;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import java.util.List;

@ApplicationScoped
public class ActivityBestEffortRepository implements PanacheRepository<ActivityBestEffort> {

    public long deleteByActivityStravaId(Long activityStravaId) {
        return delete("activityStravaId", activityStravaId);
    }

    public List<ActivityBestEffort> findTopPrsByAthleteId(Long athleteId, int limit) {
        return getEntityManager()
                .createQuery(
                        "select e from ActivityBestEffort e " +
                                "join Activity a on a.stravaId = e.activityStravaId " +
                                "where a.athleteId = :athleteId " +
                                "and e.prRank is not null " +
                                "order by e.distance desc nulls last, e.prRank asc, e.elapsedTime asc nulls last",
                        ActivityBestEffort.class)
                .setParameter("athleteId", athleteId)
                .setMaxResults(limit)
                .getResultList();
    }
}
