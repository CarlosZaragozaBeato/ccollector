package com.zensyra.collector.strava.athlete;

import com.zensyra.collector.strava.api.dto.StravaAthleteDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class AthleteUpsertService {

    private static final Logger LOG = Logger.getLogger(AthleteUpsertService.class);

    @Inject
    AthleteRepository athleteRepository;

    @Transactional
    public void upsert(StravaAthleteDto dto) {
        Athlete athlete = athleteRepository.findByStravaId(dto.getId())
                .orElseGet(Athlete::new);

        athlete.setStravaId(dto.getId());
        athlete.setUsername(dto.getUsername());
        athlete.setFirstname(dto.getFirstname());
        athlete.setLastname(dto.getLastname());
        athlete.setCity(dto.getCity());
        athlete.setCountry(dto.getCountry());
        athlete.setSex(dto.getSex());
        athlete.setProfile(dto.getProfile());
        athlete.setMeasurementPreference(dto.getMeasurementPreference());
        athlete.setFtp(dto.getFtp());
        athlete.setFollowerCount(dto.getFollowerCount());
        athlete.setFriendCount(dto.getFriendCount());
        athlete.setPremium(dto.isPremium());

        if (dto.getWeight() != null) {
            athlete.setWeight(BigDecimal.valueOf(dto.getWeight()));
        }

        athleteRepository.persist(athlete);

        LOG.infof("Athlete upserted — stravaId: %d, username: '%s'", dto.getId(), dto.getUsername());
    }
}
