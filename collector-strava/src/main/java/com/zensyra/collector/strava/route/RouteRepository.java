package com.zensyra.collector.strava.route;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class RouteRepository implements PanacheRepositoryBase<Route, Long> {

    public Optional<Route> findByStravaId(Long stravaId) {
        return find("stravaId", stravaId).firstResultOptional();
    }
}
