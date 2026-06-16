package com.zensyra.collector.strava.athlete;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class AthleteRepository implements PanacheRepositoryBase<Athlete, Long> {

    public Optional<Athlete> findByStravaId(Long stravaId) {
        return find("stravaId", stravaId).firstResultOptional();
    }
}
