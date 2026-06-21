package com.zensyra.collector.strava.identity;

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
import static org.mockito.Mockito.when;

class StravaActivityIdentityServiceTest {

    @Test
    void shouldResolveCanonicalReferenceFromStravaIds() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivityIdentityService activityIdentity = mock(ActivityIdentityService.class);
        StravaActivityIdentityService service = new StravaActivityIdentityService(accounts, activityIdentity);

        IntegrationAccount account = new IntegrationAccount(
                UUID.randomUUID(),
                IntegrationSource.STRAVA,
                "42"
        );
        ActivityReference reference = mock(ActivityReference.class);
        when(accounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "42"))
                .thenReturn(Optional.of(account));
        when(activityIdentity.resolveOrCreateReference(account.getAthleteId(), account.getId(), "999"))
                .thenReturn(reference);

        ActivityReference resolved = service.resolveOrCreateReference(42L, 999L);

        assertSame(reference, resolved);
        verify(activityIdentity).resolveOrCreateReference(account.getAthleteId(), account.getId(), "999");
    }

    @Test
    void shouldFailWhenStravaAccountDoesNotExist() {
        IntegrationAccountRepository accounts = mock(IntegrationAccountRepository.class);
        ActivityIdentityService activityIdentity = mock(ActivityIdentityService.class);
        StravaActivityIdentityService service = new StravaActivityIdentityService(accounts, activityIdentity);

        when(accounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "42"))
                .thenReturn(Optional.empty());

        assertThrows(CollectorException.class, () -> service.resolveOrCreateReference(42L, 999L));
    }
}
