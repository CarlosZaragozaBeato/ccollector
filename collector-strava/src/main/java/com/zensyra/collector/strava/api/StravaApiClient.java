package com.zensyra.collector.strava.api;

import com.zensyra.collector.strava.api.dto.StravaActivityDetailDto;
import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import com.zensyra.collector.strava.api.dto.StravaAthleteDto;
import com.zensyra.collector.strava.api.dto.StravaAthleteStatsDto;
import com.zensyra.collector.strava.api.dto.StravaAthleteZonesDto;
import com.zensyra.collector.strava.api.dto.StravaGearDto;
import com.zensyra.collector.strava.api.dto.StravaRouteDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "strava-api")
@Path("/api/v3")
@Produces(MediaType.APPLICATION_JSON)
public interface StravaApiClient {

    @GET
    @Path("/athlete")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    StravaAthleteDto getAthlete(
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/athlete/zones")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    StravaAthleteZonesDto getAthleteZones(
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/athletes/{id}/stats")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    StravaAthleteStatsDto getAthleteStats(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("id") Long athleteId
    );

    @GET
    @Path("/athlete/activities")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    List<StravaActivityDto> getActivities(
            @HeaderParam("Authorization") String bearerToken,
            @QueryParam("after") Long afterEpoch,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page
    );

    @GET
    @Path("/gear/{id}")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    StravaGearDto getGear(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("id") String gearId
    );

    @GET
    @Path("/activities/{id}")
    @Retry(maxRetries = 2, delay = 5000, jitter = 1000,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    StravaActivityDetailDto getActivityDetail(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("id") Long activityId
    );

    @GET
    @Path("/activities/{id}/streams")
    @Retry(maxRetries = 2, delay = 5000, jitter = 1000,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    Map<String, StravaActivityStreamDto> getActivityStreams(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("id") Long activityId,
            @QueryParam("keys") String keys,
            @QueryParam("key_by_type") boolean keyByType
    );

    @GET
    @Path("/athletes/{id}/routes")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    List<StravaRouteDto> getRoutes(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("id") Long athleteId,
            @QueryParam("per_page") int perPage,
            @QueryParam("page") int page
    );
}
