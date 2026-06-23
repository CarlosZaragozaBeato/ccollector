package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.support.PostgresTestResource;
import com.zensyra.collector.core.sync.IntegrationSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ActivityReferenceRepositoryFindByTrainingSessionIdTest {

    @Inject
    AthleteProfileRepository athleteProfileRepository;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    TrainingSessionRepository trainingSessionRepository;

    @Inject
    ActivityReferenceRepository activityReferenceRepository;

    @Test
    @Transactional
    void shouldReturnTheSingleSourceObservationBackingASession() {
        AthleteProfile athlete = new AthleteProfile();
        athleteProfileRepository.persistAndFlush(athlete);
        IntegrationAccount account = new IntegrationAccount(
                athlete.getId(), IntegrationSource.STRAVA, "111");
        integrationAccountRepository.persistAndFlush(account);
        TrainingSession session = new TrainingSession(athlete.getId());
        trainingSessionRepository.persistAndFlush(session);
        ActivityReference reference = new ActivityReference(
                athlete.getId(), session.getId(), account.getId(), "999");
        activityReferenceRepository.persistAndFlush(reference);

        List<ActivityReference> result =
                activityReferenceRepository.findByTrainingSessionId(session.getId());

        assertEquals(1, result.size());
        assertEquals(reference.getId(), result.get(0).getId());
    }

    @Test
    @Transactional
    void shouldReturnEmptyListWhenSessionHasNoBackingObservationYet() {
        List<ActivityReference> result =
                activityReferenceRepository.findByTrainingSessionId(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }
}
