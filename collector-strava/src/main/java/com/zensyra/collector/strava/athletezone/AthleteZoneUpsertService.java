package com.zensyra.collector.strava.athletezone;

import com.zensyra.collector.strava.api.dto.StravaAthleteZonesDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class AthleteZoneUpsertService {

    private static final Logger LOG = Logger.getLogger(AthleteZoneUpsertService.class);

    @Inject
    AthleteZoneRepository athleteZoneRepository;

    @Transactional
    public void replaceZones(Long athleteStravaId, StravaAthleteZonesDto dto) {
        upsertZoneSet(athleteStravaId, AthleteZoneType.HEART_RATE, dto != null ? dto.getHeartRateZones() : List.of());
        upsertZoneSet(athleteStravaId, AthleteZoneType.POWER, dto != null ? dto.getPowerZones() : List.of());
    }

    private void upsertZoneSet(Long athleteStravaId, AthleteZoneType zoneType, List<StravaAthleteZonesDto.Zone> zones) {
        athleteZoneRepository.delete("athleteId = ?1 and zoneType = ?2", athleteStravaId, zoneType);

        int index = 1;
        for (StravaAthleteZonesDto.Zone source : zones) {
            AthleteZone zone = new AthleteZone();
            zone.setAthleteId(athleteStravaId);
            zone.setZoneType(zoneType);
            zone.setZoneIndex(index);
            zone.setMinValue(source.getMin());
            zone.setMaxValue(source.getMax());
            athleteZoneRepository.persist(zone);
            index++;
        }

        LOG.infof("Athlete zones reemplazadas — athleteId=%d type=%s zones=%d",
                athleteStravaId, zoneType.name(), zones.size());
    }
}
