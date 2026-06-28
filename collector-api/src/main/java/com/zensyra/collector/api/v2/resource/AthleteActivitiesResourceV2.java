package com.zensyra.collector.api.v2.resource;

import com.zensyra.collector.api.v2.dto.PagedActivitiesResponseV2;
import com.zensyra.collector.query.composer.ActivityQueryComposer;
import com.zensyra.collector.query.model.QueryResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * {@code /v2} counterpart of {@code com.zensyra.collector.api.resource.AthleteActivitiesResource}.
 *
 * <p>Same canonical contract (UUID athleteId, no Strava-specific fields),
 * plus the one real difference this version exists for: a failing source
 * no longer fails the whole request. The response always returns
 * {@code 200} with whatever data could be gathered, and signals degraded
 * results explicitly via {@code partial}/{@code failures} rather than via
 * an HTTP error status — a partial result is a successful response with a
 * caveat, not a failure.
 *
 * <p>{@code /v1} is frozen and untouched; this resource does not replace it,
 * it exists alongside it under a different path.
 */
@Path("/api/v2/athletes/{athleteId}/activities")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteActivitiesResourceV2 {

    @Inject
    ActivityQueryComposer activityQueryComposer;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("type") String type,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {

        if (page < 0) {
            return ApiResponsesV2.error(Response.Status.BAD_REQUEST, "page must be >= 0");
        }
        if (size < 1 || size > 100) {
            return ApiResponsesV2.error(Response.Status.BAD_REQUEST, "size must be between 1 and 100");
        }

        Instant fromInstant;
        Instant toInstant;
        try {
            fromInstant = from != null ? Instant.parse(from) : null;
            toInstant = to != null ? Instant.parse(to) : null;
        } catch (DateTimeParseException e) {
            return ApiResponsesV2.error(Response.Status.BAD_REQUEST,
                    "Invalid date format. Expected ISO 8601 (e.g. 2025-01-01T00:00:00Z)");
        }

        QueryResult<com.zensyra.collector.query.model.Activity> result =
                activityQueryComposer.listByAthleteWithFailures(
                        athleteId, type, fromInstant, toInstant, page * size, size);

        return Response.ok(PagedActivitiesResponseV2.from(result, page, size)).build();
    }
}
