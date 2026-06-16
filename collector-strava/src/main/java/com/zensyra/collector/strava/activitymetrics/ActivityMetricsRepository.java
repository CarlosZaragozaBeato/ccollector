package com.zensyra.collector.strava.activitymetrics;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ActivityMetricsRepository implements PanacheRepositoryBase<ActivityMetrics, Long> {

    public Optional<ActivityMetrics> findByActivityId(Long activityId) {
        return findByIdOptional(activityId);
    }
}
