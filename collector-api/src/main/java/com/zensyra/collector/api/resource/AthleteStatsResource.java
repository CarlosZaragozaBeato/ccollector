package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.AthleteStatsDto;
import com.zensyra.collector.query.port.AthleteStatsQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/stats")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteStatsResource {

    @Inject
    Instance<AthleteStatsQueryPort> athleteStatsQueryPorts;

    @GET
    public Response latest(@PathParam("athleteId") UUID athleteId) {
        // Same N=1 pattern as AthleteActivityMetricsResource: no composer
        // exists yet for this port. See that resource's comment for why —
        // the same reasoning applies here unchanged.
        for (AthleteStatsQueryPort port : athleteStatsQueryPorts) {
            return port.getLatestByAthlete(athleteId)
                    .map(AthleteStatsDto::from)
                    .map(dto -> Response.ok(dto).build())
                    .orElseGet(() -> ApiResponses.error(Response.Status.NOT_FOUND,
                            "no stats found for athlete " + athleteId));
        }

        return ApiResponses.error(Response.Status.NOT_FOUND,
                "no stats found for athlete " + athleteId);
    }
}
