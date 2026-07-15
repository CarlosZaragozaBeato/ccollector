package com.zensyra.collector.suunto.api;

import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "suunto-api")
@Path("/v2")
@Produces(MediaType.APPLICATION_JSON)
public interface SuuntoApiClient {

    /**
     * Lists workouts for the athlete owning the bearer token.
     *
     * <p>Every Suunto Cloud API call requires BOTH headers: the per-athlete
     * OAuth bearer token and the per-deployment Azure APIM subscription key
     * (decrypted from {@code integration_credentials.api_subscription_key}).
     *
     * <p>Query parameters are boxed so callers can pass {@code null} to omit
     * them and let Suunto's documented server-side defaults apply
     * (since=0, until=now, limit=50, offset=0, filter-by-modification-time=true).
     * {@code since}/{@code until} are epoch milliseconds — not Strava's epoch
     * seconds — and bound modification time by default, workout start time
     * when {@code filterByModificationTime} is false.
     */
    @GET
    @Path("/workouts")
    @Retry(maxRetries = 3, delay = 2000, jitter = 500,
           retryOn = WebApplicationException.class,
           abortOn = ClientErrorException.class)
    SuuntoWorkoutListResponse getWorkouts(
            @HeaderParam("Authorization") String bearerToken,
            @HeaderParam("Ocp-Apim-Subscription-Key") String subscriptionKey,
            @QueryParam("since") Long sinceEpochMillis,
            @QueryParam("until") Long untilEpochMillis,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("filter-by-modification-time") Boolean filterByModificationTime
    );
}
