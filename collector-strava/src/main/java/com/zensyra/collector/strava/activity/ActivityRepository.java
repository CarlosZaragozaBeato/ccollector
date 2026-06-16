package com.zensyra.collector.strava.activity;

import com.zensyra.collector.strava.stream.StreamSyncStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ActivityRepository implements PanacheRepositoryBase<Activity, Long> {

    public Optional<Activity> findByStravaId(Long stravaId) {
        return find("stravaId", stravaId).firstResultOptional();
    }

    public Optional<Activity> findByAthleteIdAndStravaId(Long athleteId, Long stravaId) {
        return find("athleteId = ?1 and stravaId = ?2", athleteId, stravaId).firstResultOptional();
    }

    public List<Activity> findByAthleteId(Long athleteId) {
        return list("athleteId", athleteId);
    }

    public List<String> findDistinctGearIdsByAthleteId(Long athleteStravaId) {
        return getEntityManager()
                .createQuery(
                        "select distinct a.gearId from Activity a " +
                                "where a.athleteId = :athleteId and a.gearId is not null",
                        String.class)
                .setParameter("athleteId", athleteStravaId)
                .getResultList();
    }

    public List<Long> findStravaIdsWithoutCaloriesByAthleteId(Long athleteStravaId, int limit) {
        return getEntityManager()
                .createQuery(
                        "select a.stravaId from Activity a " +
                                "where a.athleteId = :athleteId and a.calories is null " +
                                "order by a.startDate desc",
                        Long.class)
                .setParameter("athleteId", athleteStravaId)
                .setMaxResults(limit)
                .getResultList();
    }


    public List<Long> findStravaIdsWithoutCaloriesByAthleteIdSince(
            Long athleteStravaId, Instant since, int limit) {
        return getEntityManager()
                .createQuery(
                        "select a.stravaId from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and a.calories is null " +
                                "and a.startDate >= :since " +
                                "order by a.startDate desc",
                        Long.class)
                .setParameter("athleteId", athleteStravaId)
                .setParameter("since", since)
                .setMaxResults(limit)
                .getResultList();
    }

    public Optional<Instant> findMaxStartDateByAthleteId(Long athleteId) {
        Instant maxStartDate = getEntityManager()
                .createQuery(
                        "select max(a.startDate) from Activity a where a.athleteId = :athleteId",
                        Instant.class)
                .setParameter("athleteId", athleteId)
                .getSingleResult();
        return Optional.ofNullable(maxStartDate);
    }

    // N+1 guard: callers must NOT lazily load any collection inside the returned Activity instances.
    // All data needed per activity (stravaId, streamsSyncAttempts) is mapped on the entity itself.
    public List<Activity> findPendingStreamActivitiesByAthleteId(Long athleteStravaId, int retryLimit, int limit) {
        return getEntityManager()
                .createQuery(
                        "select a from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and (" +
                                "a.streamsSyncStatus is null " +
                                "or a.streamsSyncStatus = :pendingStatus " +
                                "or (a.streamsSyncStatus = :failedStatus and a.streamsSyncAttempts < :retryLimit)" +
                                ") " +
                                "order by a.startDate desc",
                        Activity.class)
                .setParameter("athleteId", athleteStravaId)
                .setParameter("pendingStatus", StreamSyncStatus.PENDING)
                .setParameter("failedStatus", StreamSyncStatus.FAILED)
                .setParameter("retryLimit", retryLimit)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Activity> findPagedByAthleteId(Long athleteId, String type, Instant from, Instant to, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select a from Activity a where a.athleteId = :athleteId");
        if (type != null) jpql.append(" and lower(a.sportType) = lower(:type)");
        if (from != null) jpql.append(" and a.startDate >= :from");
        if (to != null)   jpql.append(" and a.startDate < :to");
        jpql.append(" order by a.startDate desc");

        var query = getEntityManager().createQuery(jpql.toString(), Activity.class)
                .setParameter("athleteId", athleteId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        if (type != null) query.setParameter("type", type);
        if (from != null) query.setParameter("from", from);
        if (to != null)   query.setParameter("to", to);
        return query.getResultList();
    }

    public List<Activity> findByAthleteIdAndDateRange(Long athleteId, Instant from, Instant to) {
        return getEntityManager()
                .createQuery(
                        "select a from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and a.startDate >= :from " +
                                "and a.startDate < :to " +
                                "order by a.startDate asc",
                        Activity.class)
                .setParameter("athleteId", athleteId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    public List<Activity> findActivitiesWithSyncedStreamsAndPower(Long athleteId) {
        return getEntityManager()
                .createQuery(
                        "select a from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and a.streamsSyncStatus = :synced " +
                                "and a.averageWatts > 0 " +
                                "order by a.startDate desc",
                        Activity.class)
                .setParameter("athleteId", athleteId)
                .setParameter("synced", StreamSyncStatus.SYNCED)
                .getResultList();
    }

    public List<Activity> findActivitiesNeedingMetricsComputation(Long athleteId) {
        return getEntityManager()
                .createQuery(
                        "select a from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and a.streamsSyncStatus = :synced " +
                                "and a.averageWatts > 0 " +
                                "and a.id not in (select m.activityId from ActivityMetrics m) " +
                                "order by a.startDate desc",
                        Activity.class)
                .setParameter("athleteId", athleteId)
                .setParameter("synced", StreamSyncStatus.SYNCED)
                .setMaxResults(100)
                .getResultList();
    }

    public long countPendingStreamActivities(long athleteId, int retryLimit) {
        return getEntityManager()
                .createQuery(
                        "select count(a) from Activity a " +
                                "where a.athleteId = :athleteId " +
                                "and (a.streamsSyncStatus is null " +
                                "or a.streamsSyncStatus = :pendingStatus " +
                                "or (a.streamsSyncStatus = :failedStatus and a.streamsSyncAttempts < :retryLimit))",
                        Long.class)
                .setParameter("athleteId", athleteId)
                .setParameter("pendingStatus", StreamSyncStatus.PENDING)
                .setParameter("failedStatus", StreamSyncStatus.FAILED)
                .setParameter("retryLimit", retryLimit)
                .getSingleResult();
    }

}
