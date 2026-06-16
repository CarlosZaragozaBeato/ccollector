package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.ActivityDto;
import com.zensyra.collector.strava.activity.ActivityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Path("/api/v1/athletes/{athleteId}/activities")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteActivitiesResource {

    @Inject
    ActivityRepository activityRepository;

    @GET
    public Response list(
            @PathParam("athleteId") Long athleteId,
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

        List<ActivityDto> items = activityRepository
                .findPagedByAthleteId(athleteId, type, fromInstant, toInstant, page * size, size)
                .stream()
                .map(ActivityDto::from)
                .toList();

        return Response.ok(Map.of("items", items, "page", page, "size", size)).build();
    }
}
