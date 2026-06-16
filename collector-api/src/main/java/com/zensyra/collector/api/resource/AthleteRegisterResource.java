package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.AthleteRegisterRequestDto;
import com.zensyra.collector.api.dto.AthleteRegisterResponseDto;
import com.zensyra.collector.api.oauth.StravaOAuthExchangeException;
import com.zensyra.collector.api.oauth.StravaOAuthService;
import com.zensyra.collector.api.oauth.StravaOAuthToken;
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

    @POST
    @Transactional
    public Response register(@Valid AthleteRegisterRequestDto request) {
        try {
            StravaOAuthToken stravaToken = stravaOAuthService
                    .exchangeAuthorizationCode(request.getCode(), request.getRedirectUri());

            OAuthToken token = tokenRepository
                    .findBySourceAndUser(IntegrationSource.STRAVA, stravaToken.athleteId())
                    .orElseGet(OAuthToken::new);

            boolean created = token.getId() == null;

            token.setSource(IntegrationSource.STRAVA);
            token.setExternalUserId(stravaToken.athleteId());
            token.setAccessToken(stravaToken.accessToken());
            token.setRefreshToken(stravaToken.refreshToken());
            token.setExpiresAt(stravaToken.expiresAt());

            if (created) {
                tokenRepository.persist(token);
            }

            AthleteRegisterResponseDto response = new AthleteRegisterResponseDto(
                    stravaToken.athleteId(),
                    created,
                    stravaToken.expiresAt()
            );

            return Response
                    .status(created ? Response.Status.CREATED : Response.Status.OK)
                    .entity(response)
                    .build();

        } catch (StravaOAuthExchangeException e) {
            return ApiResponses.error(Response.Status.BAD_GATEWAY, e.getMessage());
        }
    }
}
