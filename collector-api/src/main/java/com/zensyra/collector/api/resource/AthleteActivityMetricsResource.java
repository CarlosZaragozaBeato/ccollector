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

        // No dedicated composer exists yet for this port — unlike
        // ActivityQueryPort, which the composer merges across sources,
        // a single canonical activity has at most one metrics row per
        // source today, and there is no conflict-resolution policy defined
        // for what happens once a second source can also report metrics
        // for the same activity (see ADR-002 addendum). Taking the first
        // registered port is the correct N=1 behavior until that policy
        // exists; it must not be mistaken for "this port never needs a
        // composer."
        for (ActivityMetricsQueryPort port : activityMetricsQueryPorts) {
            return port.getByActivityId(athleteId, activityId)
                    .map(ActivityMetricsDto::from)
                    .map(dto -> Response.ok(dto).build())
                    .orElseGet(() -> ApiResponses.error(Response.Status.NOT_FOUND,
                            "no activity metrics found for activity " + activityId));
        }

        return ApiResponses.error(Response.Status.NOT_FOUND,
                "no activity metrics found for activity " + activityId);
    }
}
