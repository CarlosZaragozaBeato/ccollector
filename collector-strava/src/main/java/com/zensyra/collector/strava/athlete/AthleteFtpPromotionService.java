package com.zensyra.collector.strava.athlete;

import com.zensyra.collector.core.identity.AthleteProfile;
import com.zensyra.collector.core.identity.AthleteProfileRepository;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Promotes the athlete's FTP (functional threshold power) from the Strava
 * layer into the canonical {@link AthleteProfile}.
 *
 * <p>FTP is captured from {@code GET /api/v3/athlete} (not the zones endpoint,
 * which never exposes FTP) and already stored on {@code athletes.ftp} by
 * {@link AthleteUpsertService}. That column lives in {@code collector-strava}
 * and is invisible to {@code collector-query}/{@code collector-api} by ADR-001.
 * This service copies the value onto {@code athlete_profiles.ftp_watts}, the
 * provider-independent layer the read side can access.
 *
 * <p>All missing-data paths are silent skips, never exceptions: absence of a
 * power meter (null FTP) is the norm, and a missing canonical link on a legacy
 * or not-yet-backfilled token must not turn a successful athlete upsert into a
 * failed sync. A genuinely broken account link is already caught upstream in
 * {@code AbstractStravaJob} before this service is invoked.
 */
@ApplicationScoped
public class AthleteFtpPromotionService {

    private static final Logger LOG = Logger.getLogger(AthleteFtpPromotionService.class);

    @Inject
    IntegrationAccountRepository integrationAccountRepository;

    @Inject
    AthleteProfileRepository athleteProfileRepository;

    /**
     * Copies {@code ftp} onto the canonical athlete profile resolved from the
     * token's integration account. No-op when FTP is null or the canonical
     * profile cannot be resolved.
     *
     * @param token the Strava OAuth token being synced
     * @param ftp   the FTP value from the Strava athlete DTO, may be null
     */
    @Transactional
    public void promoteFtp(OAuthToken token, Integer ftp) {
        if (ftp == null) {
            return; // no power meter — expected for most athletes
        }

        UUID integrationAccountId = token.getIntegrationAccountId();
        if (integrationAccountId == null) {
            LOG.debugf("Skipping FTP promotion — legacy token with no integration account, user '%s'",
                    token.getExternalUserId());
            return;
        }

        IntegrationAccount account = integrationAccountRepository
                .findByIdOptional(integrationAccountId).orElse(null);
        if (account == null) {
            LOG.debugf("Skipping FTP promotion — integration account %s not found", integrationAccountId);
            return;
        }

        AthleteProfile profile = athleteProfileRepository
                .findByIdOptional(account.getAthleteId()).orElse(null);
        if (profile == null) {
            LOG.debugf("Skipping FTP promotion — athlete profile %s not found", account.getAthleteId());
            return;
        }

        profile.setFtpWatts(ftp);
        LOG.infof("FTP promoted to canonical profile — athleteId=%s ftpWatts=%d",
                account.getAthleteId(), ftp);
    }
}
