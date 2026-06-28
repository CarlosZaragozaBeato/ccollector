package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.TrainingLoadDto;
import com.zensyra.collector.query.port.TrainingLoadQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/training-load")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteTrainingLoadResource {

    @Inject
    Instance<TrainingLoadQueryPort> trainingLoadQueryPorts;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("days") @DefaultValue("30") int days) {

        if (days < 1 || days > 90) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "days must be between 1 and 90");
        }

        LocalDate from = LocalDate.now().minusDays(days - 1);

        // Same N=1 pattern as AthleteActivityMetricsResource and
        // AthleteStatsResource: no composer exists yet for this port.
        for (TrainingLoadQueryPort port : trainingLoadQueryPorts) {
            List<TrainingLoadDto> items = port.listRecentByAthlete(athleteId, from)
                    .stream()
                    .map(TrainingLoadDto::from)
                    .toList();
            return Response.ok(Map.of("days", days, "items", items)).build();
        }

        return Response.ok(Map.of("days", days, "items", List.of())).build();
    }
}
