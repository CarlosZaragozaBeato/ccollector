package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.AthleteRegisterResponseDto;
import com.zensyra.collector.api.dto.SuuntoAthleteRegisterRequestDto;
import com.zensyra.collector.api.oauth.SuuntoOAuthExchangeException;
import com.zensyra.collector.api.oauth.SuuntoOAuthService;
import com.zensyra.collector.api.oauth.SuuntoOAuthToken;
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

@Path("/api/v1/athletes/register/suunto")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SuuntoAthleteRegisterResource {

    @Inject
    SuuntoOAuthService suuntoOAuthService;

    @Inject
    OAuthTokenRepository tokenRepository;

    @Inject
    AthleteIdentityService athleteIdentityService;

    @POST
    @Transactional
    public Response register(@Valid SuuntoAthleteRegisterRequestDto request) {
        try {
            SuuntoOAuthToken suuntoToken = suuntoOAuthService
                    .exchangeAuthorizationCode(request.getCode(), request.getRedirectUri());

            IntegrationAccount integrationAccount = athleteIdentityService.resolveOrCreateAccount(
                    IntegrationSource.SUUNTO,
                    suuntoToken.user()
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
            token.setAccessToken(suuntoToken.accessToken());
            token.setRefreshToken(suuntoToken.refreshToken());
            token.setExpiresAt(suuntoToken.expiresAt());
            token.setScope(suuntoToken.scope());

            if (created) {
                tokenRepository.persist(token);
            }

            AthleteRegisterResponseDto response = new AthleteRegisterResponseDto(
                    integrationAccount.getAthleteId().toString(),
                    created,
                    suuntoToken.expiresAt()
            );

            return Response
                    .status(created ? Response.Status.CREATED : Response.Status.OK)
                    .entity(response)
                    .build();

        } catch (SuuntoOAuthExchangeException e) {
            // SuuntoOAuthService builds this message from the HTTP status code and a
            // generic description only — never the raw Suunto response body — so
            // reflecting it into the 502 body cannot leak the upstream payload.
            return ApiResponses.error(Response.Status.BAD_GATEWAY, e.getMessage());
        }
    }
}
