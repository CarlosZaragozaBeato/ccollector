package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.AthleteRegisterRequestDto;
import com.zensyra.collector.api.dto.AthleteRegisterResponseDto;
import com.zensyra.collector.api.oauth.StravaOAuthExchangeException;
import com.zensyra.collector.api.oauth.StravaOAuthService;
import com.zensyra.collector.api.oauth.StravaOAuthToken;
import com.zensyra.collector.core.identity.AthleteIdentityService;
import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/athletes/register")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteRegisterResource {

    @Inject
    StravaOAuthService stravaOAuthService;

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    AthleteIdentityService athleteIdentityService;

    @POST
    @Transactional
    public Response register(@Valid AthleteRegisterRequestDto request) {
        try {
            StravaOAuthToken stravaToken = stravaOAuthService
                    .exchangeAuthorizationCode(request.getCode(), request.getRedirectUri(), request.getScope());

            IntegrationAccount integrationAccount = athleteIdentityService.resolveOrCreateAccount(
                    IntegrationSource.STRAVA,
                    stravaToken.athleteId()
            );

            OAuthToken token = tokenRepository
                    .findByIntegrationAccountId(integrationAccount.getId())
                    .or(() -> tokenRepository.findBySourceAndUser(
                            integrationAccount.getSource(),
                            integrationAccount.getExternalUserId()))
                    .orElseGet(OAuthToken::new);

            boolean created = token.getId() == null;

            token.setIntegrationAccountId(integrationAccount.getId());
            token.setSource(integrationAccount.getSource());
            token.setExternalUserId(integrationAccount.getExternalUserId());
            token.setAccessToken(stravaToken.accessToken());
            token.setRefreshToken(stravaToken.refreshToken());
            token.setExpiresAt(stravaToken.expiresAt());
            token.setScope(stravaToken.scope());

            if (created) {
                tokenRepository.persist(token);
            }

            AthleteRegisterResponseDto response = new AthleteRegisterResponseDto(
                    integrationAccount.getAthleteId().toString(),
                    created,
                    stravaToken.expiresAt()
            );

            return Response
                    .status(created ? Response.Status.CREATED : Response.Status.OK)
                    .entity(response)
                    .build();

        } catch (StravaOAuthExchangeException e) {
            // StravaOAuthService builds this message from the HTTP status code and a
            // generic description only — never the raw Strava response body — so
            // reflecting it into the 502 body cannot leak the upstream payload.
            // The message still carries the status code, enough to diagnose the
            // failure. 502 stays 502; only the body content is affected.
            return ApiResponses.error(Response.Status.BAD_GATEWAY, e.getMessage());
        }
    }
}
