package com.zensyra.collector.strava.activity;

import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ActivityUpsertServiceTest {

    @Inject
    ActivityUpsertService upsertService;

    @Inject
    ActivityRepository activityRepository;

    @Test
    @TestTransaction
    void shouldBeIdempotent() {
        var dto = buildDto(999999L, "Test Run");

        upsertService.upsert(dto);
        upsertService.upsert(dto); // segunda vez — no debe duplicar ni lanzar excepción

        long count = activityRepository.count("stravaId", 999999L);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void shouldUpdateNameOnSecondUpsert() {
        var first = buildDto(999998L, "Morning Run");
        upsertService.upsert(first);

        var updated = buildDto(999998L, "Evening Run");
        upsertService.upsert(updated);

        var activity = activityRepository.find("stravaId", 999998L).firstResult();
        assertEquals("Evening Run", activity.getName());
    }

    private StravaActivityDto buildDto(Long stravaId, String name) {
        // Subclase anónima para proveer athleteId sin setter en el DTO (campo privado)
        return new StravaActivityDto() {
            {
                setId(stravaId);
                setName(name);
            }

            @Override
            public Long getAthleteId() {
                return 42L;
            }
        };
    }
}
