package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.TrainingLoadSummaryDto;
import com.zensyra.collector.query.model.Granularity;
import com.zensyra.collector.query.port.TrainingLoadSummaryQueryPort;
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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/training-load/summary")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class TrainingLoadSummaryResource {

    @Inject
    Instance<TrainingLoadSummaryQueryPort> trainingLoadSummaryQueryPorts;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("granularity") @DefaultValue("weekly") String granularityParam,
            @QueryParam("from") String fromParam,
            @QueryParam("to") String toParam) {

        Granularity granularity;
        try {
            granularity = Granularity.valueOf(granularityParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "granularity must be 'weekly' or 'monthly'");
        }

        LocalDate from;
        LocalDate to;
        try {
            from = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().minusMonths(3);
            to = toParam != null ? LocalDate.parse(toParam) : LocalDate.now();
        } catch (DateTimeParseException e) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)");
        }

        if (from.isAfter(to)) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'from' must not be after 'to'");
        }

        for (TrainingLoadSummaryQueryPort port : trainingLoadSummaryQueryPorts) {
            List<TrainingLoadSummaryDto> items = port.listByAthlete(athleteId, from, to, granularity)
                    .stream()
                    .map(TrainingLoadSummaryDto::from)
                    .toList();
            return Response.ok(Map.of("granularity", granularity.name(), "items", items)).build();
        }

        return Response.ok(Map.of("granularity", granularity.name(), "items", List.of())).build();
    }
}
