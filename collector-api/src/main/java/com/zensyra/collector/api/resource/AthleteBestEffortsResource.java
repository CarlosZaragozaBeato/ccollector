package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.BestEffortDto;
import com.zensyra.collector.strava.besteffort.ActivityBestEffortRepository;
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

import java.util.List;
import java.util.Map;

@Path("/api/v1/athletes/{athleteId}/best-efforts")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteBestEffortsResource {

    @Inject
    ActivityBestEffortRepository bestEffortRepository;

    @GET
    public Response list(
            @PathParam("athleteId") Long athleteId,
            @QueryParam("limit") @DefaultValue("10") int limit) {

        if (limit < 1 || limit > 50) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "limit must be between 1 and 50");
        }

        List<BestEffortDto> items = bestEffortRepository.findTopPrsByAthleteId(athleteId, limit)
                .stream()
                .map(BestEffortDto::from)
                .toList();

        return Response.ok(Map.of("items", items, "limit", limit)).build();
    }
}
