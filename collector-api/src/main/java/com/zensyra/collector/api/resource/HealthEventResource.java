package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.HealthEventDto;
import com.zensyra.collector.api.dto.HealthEventRequestDto;
import com.zensyra.collector.journal.JournalFieldLimits;
import com.zensyra.collector.journal.service.HealthEventService;
import com.zensyra.collector.query.model.HealthEventSummary;
import com.zensyra.collector.query.port.HealthEventQueryPort;
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

@Path("/api/v1/athletes/{athleteId}/health-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class HealthEventResource {

    /**
     * Mandatory, verbatim safety disclaimer. This exact text must appear in the
     * POST endpoint's OpenAPI description — the platform records and flags, it
     * never prescribes.
     */
    public static final String MEDICAL_DISCLAIMER =
            "Health and medication entries are for personal tracking only. Always consult "
                    + "your doctor before making any health or medication decisions.";

    private static final Set<String> VALID_TYPES =
            Set.of("ILLNESS", "INJURY", "MEDICATION_FLAG", "OTHER");

    @Inject
    Instance<HealthEventQueryPort> queryPorts;

    @Inject
    HealthEventService healthEventService;

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

        for (HealthEventQueryPort port : queryPorts) {
            List<HealthEventDto> items = port.findByAthlete(athleteId, from, to)
                    .stream()
                    .map(HealthEventDto::from)
                    .toList();
            return Response.ok(items).build();
        }

        return Response.ok(List.of()).build();
    }

    @POST
    @Operation(
            summary = "Record a health event",
            description = MEDICAL_DISCLAIMER)
    public Response create(
            @PathParam("athleteId") UUID athleteId,
            HealthEventRequestDto request) {

        if (request == null || request.getStartDate() == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'startDate' is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'title' is required");
        }
        // length() (UTF-16 units) >= code points, so this is conservative vs. the
        // varchar(255) column — it can never let an over-long value reach the DB.
        if (request.getTitle().length() > JournalFieldLimits.SHORT_TEXT_MAX) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "'title' must not exceed " + JournalFieldLimits.SHORT_TEXT_MAX + " characters");
        }
        if (request.getNotes() != null && request.getNotes().length() > JournalFieldLimits.NOTES_MAX) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "'notes' must not exceed " + JournalFieldLimits.NOTES_MAX + " characters");
        }
        if (request.getType() == null) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'type' is required");
        }
        if (!VALID_TYPES.contains(request.getType())) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "type must be one of: ILLNESS, INJURY, MEDICATION_FLAG, OTHER");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "'endDate' must not be before 'startDate'");
        }

        HealthEventSummary summary = healthEventService.create(
                athleteId, request.getStartDate(), request.getEndDate(),
                request.getType(), request.getTitle(), request.getNotes());

        return Response.status(Response.Status.CREATED)
                .entity(HealthEventDto.from(summary))
                .build();
    }
}
