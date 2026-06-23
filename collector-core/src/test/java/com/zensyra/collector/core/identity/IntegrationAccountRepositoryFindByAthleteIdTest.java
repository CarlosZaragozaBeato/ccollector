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
class IntegrationAccountRepositoryFindByAthleteIdTest {

    @Inject
    AthleteProfileRepository athleteProfileRepository;

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Test
    @Transactional
    void shouldListEveryAccountOwnedByAthlete() {
        AthleteProfile athlete = new AthleteProfile();
        athleteProfileRepository.persistAndFlush(athlete);

        IntegrationAccount stravaAccount = new IntegrationAccount(
                athlete.getId(), IntegrationSource.STRAVA, "111");
        integrationAccountRepository.persist(stravaAccount);

        AthleteProfile otherAthlete = new AthleteProfile();
        athleteProfileRepository.persistAndFlush(otherAthlete);
        IntegrationAccount otherAccount = new IntegrationAccount(
                otherAthlete.getId(), IntegrationSource.STRAVA, "222");
        integrationAccountRepository.persistAndFlush(otherAccount);
        integrationAccountRepository.persistAndFlush(stravaAccount);

        List<IntegrationAccount> result = integrationAccountRepository.findByAthleteId(athlete.getId());

        assertEquals(1, result.size());
        assertEquals(stravaAccount.getId(), result.get(0).getId());
    }

    @Test
    @Transactional
    void shouldReturnEmptyListForAthleteWithNoConnectedAccounts() {
        AthleteProfile athlete = new AthleteProfile();
        athleteProfileRepository.persistAndFlush(athlete);

        List<IntegrationAccount> result = integrationAccountRepository.findByAthleteId(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }
}
