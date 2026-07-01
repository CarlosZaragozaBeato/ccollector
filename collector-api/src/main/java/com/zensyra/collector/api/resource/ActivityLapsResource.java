package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.LapDto;
import com.zensyra.collector.query.port.LapQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/activities/{activityId}/laps")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ActivityLapsResource {

    @Inject
    Instance<LapQueryPort> lapQueryPorts;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @PathParam("activityId") UUID activityId) {

        // Same N=1 pattern as the other single-source ports.
        for (LapQueryPort port : lapQueryPorts) {
            List<LapDto> items = port.listByActivity(athleteId, activityId)
                    .stream()
                    .map(LapDto::from)
                    .toList();
            return Response.ok(Map.of("items", items)).build();
        }

        return Response.ok(Map.of("items", List.of())).build();
    }
}
