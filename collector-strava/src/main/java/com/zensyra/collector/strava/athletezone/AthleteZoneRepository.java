package com.zensyra.collector.strava.athletezone;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AthleteZoneRepository implements PanacheRepositoryBase<AthleteZone, Long> {

    public List<AthleteZone> findByAthleteId(Long athleteId) {
        return list("athleteId = ?1 order by zoneType asc, zoneIndex asc", athleteId);
    }
}
