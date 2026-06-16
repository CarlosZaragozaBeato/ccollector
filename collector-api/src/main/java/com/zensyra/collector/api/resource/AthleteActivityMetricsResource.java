package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.ActivityMetricsDto;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.activitymetrics.ActivityMetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/athletes/{athleteId}/activity-metrics")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteActivityMetricsResource {

    @Inject
    ActivityRepository activityRepository;

    @Inject
    ActivityMetricsRepository activityMetricsRepository;

    @GET
    public Response get(
            @PathParam("athleteId") Long athleteId,
            @QueryParam("activityId") Long activityStravaId) {

        if (activityStravaId == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "activityId is required");
        }

        Activity activity = activityRepository.findByAthleteIdAndStravaId(athleteId, activityStravaId)
                .orElse(null);
        if (activity == null) {
            return ApiResponses.error(Response.Status.NOT_FOUND,
                    "activity not found for athlete " + athleteId + ": " + activityStravaId);
        }

        return activityMetricsRepository.findByActivityId(activity.getId())
                .map(metrics -> ActivityMetricsDto.from(metrics, activityStravaId))
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> ApiResponses.error(Response.Status.NOT_FOUND,
                        "no activity metrics found for activity " + activityStravaId));
    }
}
