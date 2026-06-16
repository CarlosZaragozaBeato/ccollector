package com.zensyra.collector.strava.gear;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class GearRepository implements PanacheRepositoryBase<Gear, Long> {

    public Optional<Gear> findByStravaId(String stravaId) {
        return find("stravaId", stravaId).firstResultOptional();
    }
}
