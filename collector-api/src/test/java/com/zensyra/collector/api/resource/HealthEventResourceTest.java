package com.zensyra.collector.api.resource;

import com.zensyra.collector.journal.service.HealthEventService;
import com.zensyra.collector.query.model.HealthEventSummary;
import com.zensyra.collector.query.port.HealthEventQueryPort;
import com.zensyra.collector.query.port.TrainingDayQueryPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class HealthEventResourceTest {

    private static final String API_KEY = "test-api-key";
    private static final UUID ATHLETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @InjectMock
    HealthEventQueryPort healthEventQueryPort;

    @InjectMock
    HealthEventService healthEventService;

    @InjectMock
    TrainingDayQueryPort trainingDayQueryPort;

    private HealthEventSummary sampleSummary() {
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        return new HealthEventSummary(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5),
                "ILLNESS", "Flu", "Fever and rest", now, now);
    }

    // ---- POST: create ----

    @Test
    void shouldCreateHealthEventAndReturn201() {
        when(healthEventService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any()))
                .thenReturn(sampleSummary());

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"endDate\":\"2025-06-05\","
                        + "\"type\":\"ILLNESS\",\"title\":\"Flu\",\"notes\":\"Fever and rest\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then()
                .statusCode(201)
                .body("id", is("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .body("startDate", is("2025-06-01"))
                .body("endDate", is("2025-06-05"))
                .body("type", is("ILLNESS"))
                .body("title", is("Flu"))
                .body("notes", is("Fever and rest"))
                .body("$", not(hasKey("athleteId")));
    }

    @Test
    void shouldCreateOngoingEventWithNullEndDate() {
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        when(healthEventService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any()))
                .thenReturn(new HealthEventSummary(
                        UUID.randomUUID(), LocalDate.of(2025, 6, 1), null,
                        "INJURY", "Sprained ankle", null, now, now));

        given()
                .header("X-API-Key", API_KEY)
                .contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"INJURY\",\"title\":\"Sprained ankle\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then()
                .statusCode(201)
                .body("endDate", is((Object) null))
                .body("type", is("INJURY"));
    }

    // ---- POST: 400 validation paths ----

    @Test
    void shouldReturn400WhenStartDateMissing() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"type\":\"ILLNESS\",\"title\":\"Flu\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'startDate' is required"));
    }

    @Test
    void shouldReturn400WhenTitleMissing() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'title' is required"));
    }

    @Test
    void shouldReturn400WhenTitleBlank() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"   \"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'title' is required"));
    }

    @Test
    void shouldReturn400WhenTypeMissing() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"title\":\"Flu\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'type' is required"));
    }

    @Test
    void shouldReturn400WhenTypeInvalid() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"DOSAGE\",\"title\":\"Flu\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("type must be one of: ILLNESS, INJURY, MEDICATION_FLAG, OTHER"));
    }

    @Test
    void shouldReturn400WhenEndDateBeforeStartDate() {
        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-10\",\"endDate\":\"2025-06-01\","
                        + "\"type\":\"ILLNESS\",\"title\":\"Flu\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("'endDate' must not be before 'startDate'"));
    }

    // ---- GET ----

    @Test
    void shouldReturnHealthEventsForDateRange() {
        when(healthEventQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any()))
                .thenReturn(List.of(sampleSummary()));

        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-01").queryParam("to", "2025-06-30")
                .when().get("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", is("Flu"))
                .body("[0]", not(hasKey("athleteId")));
    }

    @Test
    void shouldReturn400ForInvalidDateFormat() {
        given().header("X-API-Key", API_KEY).queryParam("from", "not-a-date")
                .when().get("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("Invalid date format. Expected ISO 8601 date (e.g. 2025-01-01)"));
    }

    @Test
    void shouldReturn400WhenFromAfterTo() {
        given().header("X-API-Key", API_KEY)
                .queryParam("from", "2025-06-30").queryParam("to", "2025-06-01")
                .when().get("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400).body("error", is("'from' must not be after 'to'"));
    }

    @Test
    void shouldReturn401WithoutApiKey() {
        given().when().get("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(401);
    }

    // ---- Disclaimer ----

    @Test
    void disclaimerConstantIsVerbatim() {
        assertEquals(
                "Health and medication entries are for personal tracking only. Always consult "
                        + "your doctor before making any health or medication decisions.",
                HealthEventResource.MEDICAL_DISCLAIMER);
    }

    @Test
    void disclaimerAppearsVerbatimInOpenApiSpec() {
        // Request JSON: the default YAML output line-folds long descriptions,
        // which would split the sentence across lines.
        String spec = given().header("X-API-Key", API_KEY)
                .queryParam("format", "JSON")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .extract().asString();

        org.junit.jupiter.api.Assertions.assertTrue(
                spec.contains(HealthEventResource.MEDICAL_DISCLAIMER),
                "OpenAPI spec must contain the medical disclaimer verbatim");
    }

    // ---- GET default range parity with TrainingDay ----

    @Test
    void getDefaultDateRangeMatchesTrainingDay() {
        when(healthEventQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any())).thenReturn(List.of());
        when(trainingDayQueryPort.findByAthlete(eq(ATHLETE_ID), any(), any())).thenReturn(List.of());

        given().header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/health-events", ATHLETE_ID).then().statusCode(200);
        given().header("X-API-Key", API_KEY)
                .when().get("/api/v1/athletes/{id}/training-days", ATHLETE_ID).then().statusCode(200);

        ArgumentCaptor<LocalDate> heFrom = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> heTo = ArgumentCaptor.forClass(LocalDate.class);
        verify(healthEventQueryPort).findByAthlete(eq(ATHLETE_ID), heFrom.capture(), heTo.capture());

        ArgumentCaptor<LocalDate> tdFrom = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> tdTo = ArgumentCaptor.forClass(LocalDate.class);
        verify(trainingDayQueryPort).findByAthlete(eq(ATHLETE_ID), tdFrom.capture(), tdTo.capture());

        // Parity: both endpoints derive the same default window from the same expression.
        assertEquals(tdFrom.getValue(), heFrom.getValue());
        assertEquals(tdTo.getValue(), heTo.getValue());
        // And it is exactly now().minusMonths(3) .. now() (robust: from == to.minusMonths(3)).
        assertEquals(heTo.getValue().minusMonths(3), heFrom.getValue());
    }

    // ---- field length limits (#37) ----

    @Test
    void shouldAcceptTitleAtMaxLength() {
        when(healthEventService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any()))
                .thenReturn(sampleSummary());
        String title = "a".repeat(255);

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"" + title + "\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(201);
    }

    @Test
    void shouldReturn400WhenTitleExceedsMaxLength() {
        String title = "a".repeat(256);

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"" + title + "\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("'title' must not exceed 255 characters"));
    }

    @Test
    void shouldAcceptNotesAtMaxLength() {
        when(healthEventService.create(eq(ATHLETE_ID), any(), any(), any(), any(), any()))
                .thenReturn(sampleSummary());
        String notes = "a".repeat(5000);

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"Flu\",\"notes\":\""
                        + notes + "\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(201);
    }

    @Test
    void shouldReturn400WhenNotesExceedsMaxLength() {
        String notes = "a".repeat(5001);

        given().header("X-API-Key", API_KEY).contentType("application/json")
                .body("{\"startDate\":\"2025-06-01\",\"type\":\"ILLNESS\",\"title\":\"Flu\",\"notes\":\""
                        + notes + "\"}")
                .when().post("/api/v1/athletes/{id}/health-events", ATHLETE_ID)
                .then().statusCode(400)
                .body("error", is("'notes' must not exceed 5000 characters"));
    }
}
