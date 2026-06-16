package com.zensyra.collector.strava.stream;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ActivityStreamRepository implements PanacheRepositoryBase<ActivityStream, ActivityStreamId> {

    public long deleteByActivityId(Long activityId) {
        return delete("id.activityId", activityId);
    }

    public long countByActivityId(Long activityId) {
        return count("id.activityId", activityId);
    }

    public List<Integer> findWattsByActivityIdOrdered(Long activityId) {
        return getEntityManager()
                .createQuery(
                        "select s.watts from ActivityStream s " +
                                "where s.id.activityId = :activityId " +
                                "and s.watts is not null " +
                                "order by s.id.elapsedSeconds asc",
                        Integer.class)
                .setParameter("activityId", activityId)
                .getResultList();
    }
}
