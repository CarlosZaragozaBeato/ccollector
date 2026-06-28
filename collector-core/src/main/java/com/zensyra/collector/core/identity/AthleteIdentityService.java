package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class AthleteIdentityService {

    private final AthleteProfileRepository athleteProfileRepository;
    private final IntegrationAccountRepository integrationAccountRepository;

    @Inject
    public AthleteIdentityService(
            AthleteProfileRepository athleteProfileRepository,
            IntegrationAccountRepository integrationAccountRepository) {
        this.athleteProfileRepository = athleteProfileRepository;
        this.integrationAccountRepository = integrationAccountRepository;
    }

    @Transactional
    public IntegrationAccount resolveOrCreateAccount(IntegrationSource source, String externalUserId) {
        validateAccountIdentity(source, externalUserId);

        return integrationAccountRepository.findBySourceAndExternalUserId(source, externalUserId)
                .orElseGet(() -> createAccountForNewAthlete(source, externalUserId));
    }

    @Transactional
    public IntegrationAccount resolveOrCreateAccount(
            UUID athleteId,
            IntegrationSource source,
            String externalUserId) {
        validateAthleteId(athleteId);
        validateAccountIdentity(source, externalUserId);
        ensureAthleteExists(athleteId);

        return integrationAccountRepository.findBySourceAndExternalUserId(source, externalUserId)
                .map(account -> validateExistingAccount(account, athleteId))
                .orElseGet(() -> createAccount(athleteId, source, externalUserId));
    }

    private IntegrationAccount createAccountForNewAthlete(
            IntegrationSource source,
            String externalUserId) {
        AthleteProfile athleteProfile = new AthleteProfile();
        athleteProfileRepository.persistAndFlush(athleteProfile);
        return createAccount(athleteProfile.getId(), source, externalUserId);
    }

    private IntegrationAccount createAccount(
            UUID athleteId,
            IntegrationSource source,
            String externalUserId) {
        IntegrationAccount account = new IntegrationAccount(athleteId, source, externalUserId);
        integrationAccountRepository.persist(account);
        return account;
    }

    private IntegrationAccount validateExistingAccount(IntegrationAccount account, UUID athleteId) {
        if (!account.getAthleteId().equals(athleteId)) {
            throw new CollectorException(
                    "External account %s/%s already belongs to athlete %s"
                            .formatted(account.getSource(), account.getExternalUserId(), account.getAthleteId()));
        }
        return account;
    }

    private void ensureAthleteExists(UUID athleteId) {
        if (athleteProfileRepository.findByIdOptional(athleteId).isEmpty()) {
            throw new CollectorException("Athlete profile not found: " + athleteId);
        }
    }

    private void validateAthleteId(UUID athleteId) {
        if (athleteId == null) {
            throw new CollectorException("Athlete id is required");
        }
    }

    private void validateAccountIdentity(IntegrationSource source, String externalUserId) {
        if (source == null) {
            throw new CollectorException("Integration source is required");
        }
        if (externalUserId == null || externalUserId.isBlank()) {
            throw new CollectorException("External user id is required");
        }
    }
}
