package com.zensyra.collector.suunto.workout;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SuuntoWorkoutRepository implements PanacheRepository<SuuntoWorkout> {

    public Optional<SuuntoWorkout> findByWorkoutKey(String workoutKey) {
        return find("workoutKey", workoutKey).firstResultOptional();
    }

    /**
     * Highest {@code lastModified} (epoch ms) among this user's synced rows —
     * the incremental-sync watermark. Empty when the user has no rows yet
     * (first run → full history).
     */
    public Optional<Long> findMaxLastModifiedByUser(String suuntoUser) {
        Long max = getEntityManager()
                .createQuery(
                        "select max(w.lastModified) from SuuntoWorkout w where w.suuntoUser = :suuntoUser",
                        Long.class)
                .setParameter("suuntoUser", suuntoUser)
                .getSingleResult();
        return Optional.ofNullable(max);
    }

    public List<SuuntoWorkout> findByUserAndDateRange(String suuntoUser, Instant from, Instant to) {
        return list("suuntoUser = ?1 and startDate >= ?2 and startDate < ?3", suuntoUser, from, to);
    }

    // Same dynamic-filter shape as collector-strava's
    // ActivityRepository.findPagedByAthleteId, keyed by the Suunto user string.
    public List<SuuntoWorkout> findPagedByUser(
            String suuntoUser, String sportType, Instant from, Instant to, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select w from SuuntoWorkout w where w.suuntoUser = :suuntoUser");
        if (sportType != null) jpql.append(" and lower(w.sportType) = lower(:sportType)");
        if (from != null) jpql.append(" and w.startDate >= :from");
        if (to != null)   jpql.append(" and w.startDate < :to");
        jpql.append(" order by w.startDate desc");

        var query = getEntityManager().createQuery(jpql.toString(), SuuntoWorkout.class)
                .setParameter("suuntoUser", suuntoUser)
                .setFirstResult(offset)
                .setMaxResults(limit);
        if (sportType != null) query.setParameter("sportType", sportType);
        if (from != null) query.setParameter("from", from);
        if (to != null)   query.setParameter("to", to);
        return query.getResultList();
    }
}
