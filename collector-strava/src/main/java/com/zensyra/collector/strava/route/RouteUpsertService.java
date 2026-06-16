package com.zensyra.collector.strava.route;

import com.zensyra.collector.strava.api.dto.StravaRouteDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RouteUpsertService {

    private static final Logger LOG = Logger.getLogger(RouteUpsertService.class);

    @Inject
    RouteRepository routeRepository;

    @Transactional
    public void upsert(StravaRouteDto dto) {
        Route route = routeRepository.findByStravaId(dto.getId())
                .orElseGet(Route::new);

        route.setStravaId(dto.getId());
        route.setAthleteId(dto.getAthleteId());
        route.setName(dto.getName());
        route.setDistance(dto.getDistance());
        route.setElevationGain(dto.getElevationGain());
        route.setType(dto.getType());
        route.setPolyline(dto.getSummaryPolyline());
        if (route.getCreatedAt() == null) {
            route.setCreatedAt(dto.getCreatedAtAsInstant());
        }

        routeRepository.persist(route);
        LOG.debugf("Route upserted — stravaId: %d, athlete: %d", dto.getId(), dto.getAthleteId());
    }
}
