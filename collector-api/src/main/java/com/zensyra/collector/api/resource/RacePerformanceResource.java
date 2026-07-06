package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.RacePerformanceDto;
import com.zensyra.collector.query.composer.RacePerformanceComposer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

@Path("/api/v1/athletes/{athleteId}/race-performance")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class RacePerformanceResource {

    // Direct injection (not Instance<>): RacePerformanceComposer is a single
    // concrete collector-query bean that composes the underlying ports itself —
    // it is not a per-source port with multiple implementations.
    @Inject
    RacePerformanceComposer racePerformanceComposer;

    @GET
    @Operation(
            summary = "Race-performance correlation (PMC context per race)",
            description = "For each race in the window, returns the race result together with the athlete's "
                    + "training-load context: CTL (chronic fitness), ATL (acute fatigue) and TSB (form) on "
                    + "race day, 7 days before (short-term fatigue), and 42 days before (chronic fitness) — "
                    + "the standard Performance Management Chart look-back pairing. Each of the three points "
                    + "carries an `available` flag: when a daily training-load row exists within 3 days of the "
                    + "target date it is used (`actualDate` shows the row actually sampled), otherwise the point "
                    + "is reported as unavailable with null metrics rather than a substituted value. "
                    + "Races are returned newest first. An athlete with no races in the window yields an empty list. "
                    + "Defaults: `from` = 12 months ago, `to` = today.")
    public Response list(
            @PathParam("athleteId") UUID athleteId,
            @QueryParam("from") String fromParam,
            @QueryParam("to") String toParam) {

        LocalDate from;
        LocalDate to;
        try {
            // Races have annual cadence, so the default window is 12 months —
            // same default as the /race-results endpoint.
            from = fromParam != null ? LocalDate.parse(fromParam) : LocalDate.now().minusMonths(12);
            to = toParam != null ? LocalDate.parse(toParam) : LocalDate.now();
        } catch (DateTimeParseException e) {
            return ApiResponses.error(Response.Status.BAD_REQUEST,
                    "Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)");
        }

        if (from.isAfter(to)) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "'from' must not be after 'to'");
        }

        List<RacePerformanceDto> items = racePerformanceComposer
                .composeForAthlete(athleteId, from, to)
                .stream()
                .map(RacePerformanceDto::from)
                .toList();

        return Response.ok(items).build();
    }
}
