package com.zensyra.collector.strava.activity;

import com.zensyra.collector.core.identity.ActivityReferenceRepository;
import com.zensyra.collector.core.identity.AthleteIdentityService;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.sync.IntegrationSource;
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

    @Inject
    AthleteIdentityService athleteIdentityService;

    @Inject
    ActivityReferenceRepository activityReferenceRepository;

    @Test
    @TestTransaction
    void shouldBeIdempotent() {
        IntegrationAccount account = prepareStravaAccount();
        var dto = buildDto(999999L, "Test Run");

        upsertService.upsert(dto);
        upsertService.upsert(dto); // second time — must not duplicate or throw an exception

        long count = activityRepository.count("stravaId", 999999L);
        assertEquals(1L, count);
        assertEquals(1L, activityReferenceRepository.count());
        activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(account.getId(), "999999")
                .orElseThrow();
    }

    @Test
    @TestTransaction
    void shouldUpdateNameOnSecondUpsert() {
        prepareStravaAccount();
        var first = buildDto(999998L, "Morning Run");
        upsertService.upsert(first);

        var updated = buildDto(999998L, "Evening Run");
        upsertService.upsert(updated);

        var activity = activityRepository.find("stravaId", 999998L).firstResult();
        assertEquals("Evening Run", activity.getName());
    }

    private IntegrationAccount prepareStravaAccount() {
        return athleteIdentityService.resolveOrCreateAccount(IntegrationSource.STRAVA, "42");
    }

    private StravaActivityDto buildDto(Long stravaId, String name) {
        // Anonymous subclass provides athleteId without a DTO setter (private field).
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
