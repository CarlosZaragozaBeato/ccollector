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

class AthleteIdentityServiceTest {

    @Test
    void shouldCreateAthleteAndAccountForFirstConnection() {
        AthleteProfileRepository athleteProfiles = mock(AthleteProfileRepository.class);
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        AthleteIdentityService service = new AthleteIdentityService(athleteProfiles, integrationAccounts);
        when(integrationAccounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "123"))
                .thenReturn(Optional.empty());

        IntegrationAccount account = service.resolveOrCreateAccount(IntegrationSource.STRAVA, "123");

        assertEquals(IntegrationSource.STRAVA, account.getSource());
        assertEquals("123", account.getExternalUserId());
        verify(athleteProfiles).persistAndFlush(any(AthleteProfile.class));
        verify(integrationAccounts).persist(account);
    }

    @Test
    void shouldReturnExistingAccountForSameExternalIdentity() {
        AthleteProfileRepository athleteProfiles = mock(AthleteProfileRepository.class);
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        AthleteIdentityService service = new AthleteIdentityService(athleteProfiles, integrationAccounts);
        IntegrationAccount existing = new IntegrationAccount(
                UUID.randomUUID(), IntegrationSource.STRAVA, "123");
        when(integrationAccounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "123"))
                .thenReturn(Optional.of(existing));

        IntegrationAccount resolved = service.resolveOrCreateAccount(IntegrationSource.STRAVA, "123");

        assertSame(existing, resolved);
        verify(athleteProfiles, never()).persistAndFlush(any(AthleteProfile.class));
        verify(integrationAccounts, never()).persist(any(IntegrationAccount.class));
    }

    @Test
    void shouldRejectReassigningExternalAccountToAnotherAthlete() {
        AthleteProfileRepository athleteProfiles = mock(AthleteProfileRepository.class);
        IntegrationAccountRepository integrationAccounts = mock(IntegrationAccountRepository.class);
        AthleteIdentityService service = new AthleteIdentityService(athleteProfiles, integrationAccounts);
        UUID existingAthleteId = UUID.randomUUID();
        UUID requestedAthleteId = UUID.randomUUID();
        IntegrationAccount existing = new IntegrationAccount(
                existingAthleteId, IntegrationSource.STRAVA, "123");
        when(athleteProfiles.findByIdOptional(requestedAthleteId))
                .thenReturn(Optional.of(new AthleteProfile()));
        when(integrationAccounts.findBySourceAndExternalUserId(IntegrationSource.STRAVA, "123"))
                .thenReturn(Optional.of(existing));

        assertThrows(CollectorException.class, () -> service.resolveOrCreateAccount(
                requestedAthleteId, IntegrationSource.STRAVA, "123"));

        verify(integrationAccounts, never()).persist(any(IntegrationAccount.class));
    }
}
