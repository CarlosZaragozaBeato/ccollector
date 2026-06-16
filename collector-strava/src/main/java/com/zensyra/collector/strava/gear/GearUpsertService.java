package com.zensyra.collector.strava.gear;

import com.zensyra.collector.strava.api.dto.StravaGearDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class GearUpsertService {

    private static final Logger LOG = Logger.getLogger(GearUpsertService.class);

    @Inject
    GearRepository gearRepository;

    @Transactional
    public void upsert(StravaGearDto dto, Long athleteId) {
        Gear gear = gearRepository.findByStravaId(dto.getId())
                .orElseGet(Gear::new);

        gear.setStravaId(dto.getId());
        gear.setAthleteId(athleteId);
        gear.setName(dto.getName());
        gear.setPrimaryGear(dto.isPrimary());
        gear.setBrandName(dto.getBrandName());
        gear.setModelName(dto.getModelName());
        gear.setDescription(dto.getDescription());
        gear.setRetired(dto.isRetired());
        gear.setGearType(dto.getGearType());

        if (dto.getDistance() != null) {
            gear.setDistance(BigDecimal.valueOf(dto.getDistance()));
        }

        gearRepository.persist(gear);

        LOG.debugf("Gear upserted — stravaId: '%s', nombre: '%s'", dto.getId(), dto.getName());
    }
}
