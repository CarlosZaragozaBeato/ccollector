package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.ActivityMetricsDto;
import com.zensyra.collector.query.port.ActivityMetricsQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/activity-metrics")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteActivityMetricsResource {

    @Inject
    Instance<ActivityMetricsQueryPort> activityMetricsQueryPorts;

    @GET
    public Response get(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("activityId") UUID activityId) {

        if (activityId == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "activityId is required");
        }

        // Try each registered port in CDI iteration order; return the first hit.
        // A canonical activity id exists in exactly one source, so at most one
        // port will return a non-empty result. No conflict-resolution policy is
        // defined for the case where two sources claim the same activityId (see
        // ADR-002 addendum) — if that ever happens the result is non-deterministic
        // by design, accepted until physical-session dedup (ADR-002) is resolved.
        for (ActivityMetricsQueryPort port : activityMetricsQueryPorts) {
            Optional<com.zensyra.collector.query.model.ActivityMetrics> result =
                    port.getByActivityId(athleteId, activityId);
            if (result.isPresent()) {
                return Response.ok(ActivityMetricsDto.from(result.get())).build();
            }
        }

        return ApiResponses.error(Response.Status.NOT_FOUND,
                "no activity metrics found for activity " + activityId);
    }
}
