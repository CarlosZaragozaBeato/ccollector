package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.RaceResultDto;
import com.zensyra.collector.api.dto.RaceResultRequestDto;
import com.zensyra.collector.journal.service.RaceResultService;
import com.zensyra.collector.query.model.RaceResultSummary;
import com.zensyra.collector.query.port.RaceResultQueryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/race-results")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class RaceResultResource {

    @Inject
    Instance<RaceResultQueryPort> queryPorts;

    @Inject
    RaceResultService raceResultService;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("from") String fromParam,
            @QueryParam("to") String toParam) {

        LocalDate from;
        LocalDate to;
        try {
            // Races have annual cadence, so the default window is 12 months (not
            // the 3-month default used by TrainingDay/HealthEvent) — a 3-month
            // window would almost always return an empty list.
            from = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().minusMonths(12);
            to = toParam != null ? LocalDate.parse(toParam) : LocalDate.now();
        } catch (DateTimeParseException e) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)");
        }

        if (from.isAfter(to)) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'from' must not be after 'to'");
        }

        for (RaceResultQueryPort port : queryPorts) {
            List<RaceResultDto> items = port.findByAthlete(athleteId, from, to)
                    .stream()
                    .map(RaceResultDto::from)
                    .toList();
            return Response.ok(items).build();
        }

        return Response.ok(List.of()).build();
    }

    @POST
    @Operation(
            summary = "Record a race result",
            description = "Creates a race result for the athlete. raceDate is the correlation "
                    + "anchor with training load (CTL/ATL/TSB) on and before race day.")
    public Response create(
            @PathParam("athleteId") UUID athleteId,
            RaceResultRequestDto request) {

        if (request == null || request.getRaceDate() == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'raceDate' is required");
        }
        if (request.getRaceName() == null || request.getRaceName().isBlank()) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'raceName' is required");
        }
        if (request.getDistanceMeters() == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'distanceMeters' is required");
        }
        if (request.getDistanceMeters() <= 0) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'distanceMeters' must be positive");
        }
        if (request.getGoalFinishTime() != null && request.getGoalFinishTime() <= 0) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'goalFinishTime' must be positive");
        }
        if (request.getActualFinishTime() != null && request.getActualFinishTime() <= 0) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'actualFinishTime' must be positive");
        }
        if (request.getPosition() != null && request.getPosition() <= 0) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'position' must be positive");
        }

        RaceResultSummary summary = raceResultService.create(
                athleteId, request.getRaceDate(), request.getRaceName(), request.getDistanceMeters(),
                request.getGoalFinishTime(), request.getActualFinishTime(), request.getPosition(),
                request.getNotes(), request.getLinkedActivityId());

        return Response.status(Response.Status.CREATED)
                .entity(RaceResultDto.from(summary))
                .build();
    }
}
