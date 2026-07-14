package com.zensyra.collector.suunto.identity;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.identity.ActivityIdentityService;
import com.zensyra.collector.core.identity.ActivityReference;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SuuntoActivityIdentityServiceTest {

    @Test
    void shouldResolveCanonicalReferenceFromSuuntoIdentifiers() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivityIdentityService activityIdentity = mock(ActivityIdentityService.class);
        SuuntoActivityIdentityService service = new SuuntoActivityIdentityService(accounts, activityIdentity);

        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(),
                IntegrationSource.SUUNTO,
                "carloszaragozabeato"
        );
        ActivityReference reference = mock(ActivityReference.class);
        when(accounts.findBySourceAndExternalUserId(IntegrationSource.SUUNTO, "carloszaragozabeato"))
                .thenReturn(Optional.of(account));
        when(activityIdentity.resolveOrCreateReference(
                account.getAthleteId(), account.getId(), "5b190f5c52ce7b316acbd520"))
                .thenReturn(reference);

        ActivityReference resolved = service.resolveOrCreateReference(
                "carloszaragozabeato", "5b190f5c52ce7b316acbd520");

        assertSame(reference, resolved);
        verify(activityIdentity).resolveOrCreateReference(
                account.getAthleteId(), account.getId(), "5b190f5c52ce7b316acbd520");
    }

    @Test
    void shouldFailWhenSuuntoAccountDoesNotExist() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivityIdentityService activityIdentity = mock(ActivityIdentityService.class);
        SuuntoActivityIdentityService service = new SuuntoActivityIdentityService(accounts, activityIdentity);

        when(accounts.findBySourceAndExternalUserId(IntegrationSource.SUUNTO, "ghost-user"))
                .thenReturn(Optional.empty());

        assertThrows(CollectorException.class,
                () -> service.resolveOrCreateReference("ghost-user", "some-workout-key"));
    }

    @Test
    void shouldRejectBlankUserOrWorkoutKeyBeforeTouchingTheRepository() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivityIdentityService activityIdentity = mock(ActivityIdentityService.class);
        SuuntoActivityIdentityService service = new SuuntoActivityIdentityService(accounts, activityIdentity);

        assertThrows(CollectorException.class, () -> service.resolveOrCreateReference(null, "key"));
        assertThrows(CollectorException.class, () -> service.resolveOrCreateReference("  ", "key"));
        assertThrows(CollectorException.class, () -> service.resolveOrCreateReference("user", null));
        assertThrows(CollectorException.class, () -> service.resolveOrCreateReference("user", "  "));

        verifyNoInteractions(accounts, activityIdentity);
    }
}
