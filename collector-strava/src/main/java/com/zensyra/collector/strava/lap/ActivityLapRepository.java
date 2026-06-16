package com.zensyra.collector.strava.lap;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ActivityLapRepository implements PanacheRepositoryBase<ActivityLap, Long> {

    public List<ActivityLap> findByActivityStravaId(Long activityStravaId) {
        return list("activityStravaId", activityStravaId);
    }

    public long deleteByActivityStravaId(Long activityStravaId) {
        return delete("activityStravaId", activityStravaId);
    }
}
