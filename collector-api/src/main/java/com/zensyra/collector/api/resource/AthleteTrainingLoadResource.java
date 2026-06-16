package com.zensyra.collector.api.resource;

import com.zensyra.collector.api.dto.TrainingLoadDto;
import com.zensyra.collector.strava.trainingload.AthleteTrainingLoadRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@Path("/api/v1/athletes/{athleteId}/training-load")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AthleteTrainingLoadResource {

    @Inject
    AthleteTrainingLoadRepository trainingLoadRepository;

    @GET
    public Response list(
            @PathParam("athleteId") Long athleteId,
            @QueryParam("days") @DefaultValue("30") int days) {

        if (days < 1 || days > 90) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "days must be between 1 and 90");
        }

        LocalDate from = LocalDate.now().minusDays(days - 1);

        List<TrainingLoadDto> items = trainingLoadRepository
                .findRecentByAthleteId(athleteId, from)
                .stream()
                .map(TrainingLoadDto::from)
                .toList();

        return Response.ok(Map.of("days", days, "items", items)).build();
    }
}
