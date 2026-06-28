package com.zensyra.collector.core.identity;

import com.zensyra.collector.core.exception.CollectorException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class ActivityIdentityService {

    private final IntegrationAccountRepository integrationAccountRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ActivityReferenceRepository activityReferenceRepository;

    @Inject
    public ActivityIdentityService(
            IntegrationAccountRepository integrationAccountRepository,
            TrainingSessionRepository trainingSessionRepository,
            ActivityReferenceRepository activityReferenceRepository) {
        this.integrationAccountRepository = integrationAccountRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.activityReferenceRepository = activityReferenceRepository;
    }

    @Transactional
    public ActivityReference resolveOrCreateReference(
            UUID athleteId,
            UUID integrationAccountId,
            String externalActivityId) {
        IntegrationAccount account = resolveAccountForAthlete(athleteId, integrationAccountId);
        validateExternalActivityId(externalActivityId);

        ActivityReference existing = activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(integrationAccountId, externalActivityId)
                .orElse(null);
        if (existing != null) {
            return validateExistingReference(existing, athleteId, account.getId(), null);
        }

        TrainingSession trainingSession = new TrainingSession(athleteId);
        trainingSessionRepository.persistAndFlush(trainingSession);
        return createReference(athleteId, trainingSession.getId(), account.getId(), externalActivityId);
    }

    @Transactional
    public ActivityReference resolveOrCreateReference(
            UUID athleteId,
            UUID integrationAccountId,
            UUID trainingSessionId,
            String externalActivityId) {
        IntegrationAccount account = resolveAccountForAthlete(athleteId, integrationAccountId);
        TrainingSession trainingSession = resolveSessionForAthlete(athleteId, trainingSessionId);
        validateExternalActivityId(externalActivityId);

        return activityReferenceRepository
                .findByIntegrationAccountIdAndExternalActivityId(integrationAccountId, externalActivityId)
                .map(reference -> validateExistingReference(
                        reference, athleteId, account.getId(), trainingSession.getId()))
                .orElseGet(() -> createReference(
                        athleteId, trainingSession.getId(), account.getId(), externalActivityId));
    }

    private ActivityReference createReference(
            UUID athleteId,
            UUID trainingSessionId,
            UUID integrationAccountId,
            String externalActivityId) {
        ActivityReference reference = new ActivityReference(
                athleteId,
                trainingSessionId,
                integrationAccountId,
                externalActivityId);
        activityReferenceRepository.persist(reference);
        return reference;
    }

    private IntegrationAccount resolveAccountForAthlete(UUID athleteId, UUID integrationAccountId) {
        validateId(athleteId, "Athlete id");
        validateId(integrationAccountId, "Integration account id");

        IntegrationAccount account = integrationAccountRepository.findByIdOptional(integrationAccountId)
                .orElseThrow(() -> new CollectorException("Integration account not found: " + integrationAccountId));
        if (!account.getAthleteId().equals(athleteId)) {
            throw new CollectorException(
                    "Integration account %s does not belong to athlete %s"
                            .formatted(integrationAccountId, athleteId));
        }
        return account;
    }

    private TrainingSession resolveSessionForAthlete(UUID athleteId, UUID trainingSessionId) {
        validateId(trainingSessionId, "Training session id");

        TrainingSession trainingSession = trainingSessionRepository.findByIdOptional(trainingSessionId)
                .orElseThrow(() -> new CollectorException("Training session not found: " + trainingSessionId));
        if (!trainingSession.getAthleteId().equals(athleteId)) {
            throw new CollectorException(
                    "Training session %s does not belong to athlete %s"
                            .formatted(trainingSessionId, athleteId));
        }
        return trainingSession;
    }

    private ActivityReference validateExistingReference(
            ActivityReference reference,
            UUID athleteId,
            UUID integrationAccountId,
            UUID trainingSessionId) {
        if (!reference.getAthleteId().equals(athleteId)
                || !reference.getIntegrationAccountId().equals(integrationAccountId)) {
            throw new CollectorException("Existing activity reference has inconsistent athlete or account ownership");
        }
        if (trainingSessionId != null && !reference.getTrainingSessionId().equals(trainingSessionId)) {
            throw new CollectorException(
                    "External activity is already linked to another training session: "
                            + reference.getTrainingSessionId());
        }
        return reference;
    }

    private void validateExternalActivityId(String externalActivityId) {
        if (externalActivityId == null || externalActivityId.isBlank()) {
            throw new CollectorException("External activity id is required");
        }
    }

    private void validateId(UUID id, String name) {
        if (id == null) {
            throw new CollectorException(name + " is required");
        }
    }
}
