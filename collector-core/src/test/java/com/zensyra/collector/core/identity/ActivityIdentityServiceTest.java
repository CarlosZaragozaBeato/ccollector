package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.sync.IntegrationSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityIdentityServiceTest {

    @Test
    void shouldCreateSessionAndReferenceForNewExternalActivity() {
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        TrainingSessionRepository trainingSessions = mock(TrainingSessionRepository.class);
        ActivityReferenceRepository activityReferences = mock(ActivityReferenceRepository.class);
        ActivityIdentityService service = new ActivityIdentityService(
                integrationAccounts, trainingSessions, activityReferences);
        UUID athleteId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "123");
        when(integrationAccounts.findByIdOptional(accountId)).thenReturn(Optional.of(account));
        when(activityReferences.findByIntegrationAccountIdAndExternalActivityId(accountId, "456"))
                .thenReturn(Optional.empty());

        ActivityReference reference = service.resolveOrCreateReference(athleteId, accountId, "456");

        assertEquals(athleteId, reference.getAthleteId());
        assertEquals(account.getId(), reference.getIntegrationAccountId());
        assertEquals("456", reference.getExternalActivityId());
        verify(trainingSessions).persistAndFlush(any(TrainingSession.class));
        verify(activityReferences).persist(reference);
    }

    @Test
    void shouldRejectAnAccountFromAnotherAthlete() {
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        TrainingSessionRepository trainingSessions = mock(TrainingSessionRepository.class);
        ActivityReferenceRepository activityReferences = mock(ActivityReferenceRepository.class);
        ActivityIdentityService service = new ActivityIdentityService(
                integrationAccounts, trainingSessions, activityReferences);
        UUID requestedAthleteId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(), IntegrationSource.STRAVA, "123");
        when(integrationAccounts.findByIdOptional(accountId)).thenReturn(Optional.of(account));

        assertThrows(CollectorException.class,
                () -> service.resolveOrCreateReference(requestedAthleteId, accountId, "456"));

        verify(trainingSessions, never()).persistAndFlush(any(TrainingSession.class));
        verify(activityReferences, never()).persist(any(ActivityReference.class));
    }

    @Test
    void shouldReuseExistingReferenceForRepeatedExternalActivity() {
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        TrainingSessionRepository trainingSessions = mock(TrainingSessionRepository.class);
        ActivityReferenceRepository activityReferences = mock(ActivityReferenceRepository.class);
        ActivityIdentityService service = new ActivityIdentityService(
                integrationAccounts, trainingSessions, activityReferences);
        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "123");
        ActivityReference existing = new ActivityReference(
                athleteId,
                UUID.randomUUID(),
                account.getId(),
                "456");
        when(integrationAccounts.findByIdOptional(account.getId())).thenReturn(Optional.of(account));
        when(activityReferences.findByIntegrationAccountIdAndExternalActivityId(account.getId(), "456"))
                .thenReturn(Optional.of(existing));

        ActivityReference resolved = service.resolveOrCreateReference(athleteId, account.getId(), "456");

        assertSame(existing, resolved);
        verify(trainingSessions, never()).persistAndFlush(any(TrainingSession.class));
        verify(activityReferences, never()).persist(any(ActivityReference.class));
    }

    @Test
    void shouldRejectTrainingSessionFromAnotherAthlete() {
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        TrainingSessionRepository trainingSessions = mock(TrainingSessionRepository.class);
        ActivityReferenceRepository activityReferences = mock(ActivityReferenceRepository.class);
        ActivityIdentityService service = new ActivityIdentityService(
                integrationAccounts, trainingSessions, activityReferences);
        UUID athleteId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "123");
        TrainingSession session = new TrainingSession(UUID.randomUUID());
        when(integrationAccounts.findByIdOptional(accountId)).thenReturn(Optional.of(account));
        when(trainingSessions.findByIdOptional(sessionId)).thenReturn(Optional.of(session));

        assertThrows(CollectorException.class,
                () -> service.resolveOrCreateReference(athleteId, accountId, sessionId, "456"));

        verify(activityReferences, never()).persist(any(ActivityReference.class));
    }
}
