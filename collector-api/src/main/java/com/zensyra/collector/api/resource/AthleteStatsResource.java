package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.AthleteStatsDto;
import com.zensyra.collector.strava.athletestats.AthleteStatsSnapshotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/athletes/{athleteId}/stats")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteStatsResource {

    @Inject
    AthleteStatsSnapshotRepository snapshotRepository;

    @GET
    public Response latest(@PathParam("athleteId") Long athleteId) {
        return snapshotRepository.findLatestByAthleteId(athleteId)
                .map(AthleteStatsDto::from)
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> ApiResponses.error(Response.Status.NOT_FOUND,
                        "no stats found for athlete " + athleteId));
    }
}
