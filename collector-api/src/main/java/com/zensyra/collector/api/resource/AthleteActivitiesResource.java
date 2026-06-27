package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.ActivityDto;
import com.zensyra.collector.query.composer.ActivityQueryComposer;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/activities")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteActivitiesResource {

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
            return ApiResponses.error(Response.Status.BAD_REQUEST, "page must be >= 0");
        }
        if (size < 1 || size > 100) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "size must be between 1 and 100");
        }

        Instant fromInstant;
        Instant toInstant;
        try {
            fromInstant = from != null ? Instant.parse(from) : null;
            toInstant = to != null ? Instant.parse(to) : null;
        } catch (DateTimeParseException e) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "Invalid date format. Expected ISO 8601 (e.g. 2025-01-01T00:00:00Z)");
        }

        List<ActivityDto> items = activityQueryComposer
                .listByAthlete(athleteId, type, fromInstant, toInstant, page * size, size)
                .stream()
                .map(ActivityDto::from)
                .toList();

        return Response.ok(Map.of("items", items, "page", page, "size", size)).build();
    }
}
