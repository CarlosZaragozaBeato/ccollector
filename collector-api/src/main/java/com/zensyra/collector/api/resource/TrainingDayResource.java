package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.TrainingDayDto;
import com.zensyra.collector.api.dto.TrainingDayRequestDto;
import com.zensyra.collector.journal.JournalFieldLimits;
import com.zensyra.collector.journal.service.TrainingDayService;
import com.zensyra.collector.query.model.TrainingDaySummary;
import com.zensyra.collector.query.port.TrainingDayQueryPort;
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
import java.util.Set;
import java.util.UUID;

@Path("/api/v1/athletes/{athleteId}/training-days")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class TrainingDayResource {

    private static final Set<String> VALID_SUBJECTIVE_STATES =
            Set.of("EXCELLENT", "GOOD", "NEUTRAL", "POOR", "BAD");

    @Inject
    Instance<TrainingDayQueryPort> queryPorts;

    @Inject
    TrainingDayService trainingDayService;

    @GET
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("from") String fromParam,
            @QueryParam("to") String toParam) {

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

        for (TrainingDayQueryPort port : queryPorts) {
            List<TrainingDayDto> items = port.findByAthlete(athleteId, from, to)
                    .stream()
                    .map(TrainingDayDto::from)
                    .toList();
            return Response.ok(items).build();
        }

        return Response.ok(List.of()).build();
    }

    @POST
    @Operation(
            summary = "Record or update a training day diary entry",
            description = "Creates or updates the daily diary entry for the specified calendar date. "
                    + "All fields except 'date' are optional. When a field is present (including null), "
                    + "it overwrites any previously stored value. Omitting a field and passing null "
                    + "are treated identically — both set the field to null (see PR description for rationale). "
                    + "Medication and health-related notes are for personal tracking only. "
                    + "Always consult your doctor before making any health or medication decisions.")
    public Response upsert(
            @PathParam("athleteId") UUID athleteId,
            TrainingDayRequestDto request) {

        if (request == null || request.getDate() == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'date' is required");
        }

        if (request.getPerceivedEffort() != null
                && (request.getPerceivedEffort() < 1 || request.getPerceivedEffort() > 10)) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "perceivedEffort must be between 1 and 10");
        }

        if (request.getNotes() != null && request.getNotes().length() > JournalFieldLimits.NOTES_MAX) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "'notes' must not exceed " + JournalFieldLimits.NOTES_MAX + " characters");
        }

        String stateStr = null;
        if (request.getSubjectiveState() != null) {
            stateStr = request.getSubjectiveState().toUpperCase();
            if (!VALID_SUBJECTIVE_STATES.contains(stateStr)) {
                return ApiResponses.error(Response.Status.BAD_REQUEST,
                        "subjectiveState must be one of: EXCELLENT, GOOD, NEUTRAL, POOR, BAD");
            }
        }

        TrainingDaySummary summary = trainingDayService.upsert(
                athleteId, request.getDate(), request.getPerceivedEffort(),
                stateStr, request.getNotes(), request.getWeightKg());

        return Response.status(Response.Status.CREATED)
                .entity(TrainingDayDto.from(summary))
                .build();
    }
}
