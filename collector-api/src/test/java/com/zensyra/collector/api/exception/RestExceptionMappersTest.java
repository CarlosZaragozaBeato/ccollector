package com.zensyra.collector.api.exception;

import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deterministic unit tests for the mapping logic, using synthetic exceptions —
 * no DB. The end-to-end FK path is covered by {@code UnknownAthleteFkIT}.
 */
class RestExceptionMappersTest {

    private final RestExceptionMappers mappers = new RestExceptionMappers();

    @SuppressWarnings("unchecked")
    private static String errorOf(Response r) {
        return ((Map<String, String>) r.getEntity()).get("error");
    }

    @Test
    void fkViolation_23503_maps_to_404_athleteNotFound() {
        PersistenceException ex = new PersistenceException("insert failed",
                new SQLException("FK", "23503"));

        Response r = mappers.mapPersistence(ex);

        assertEquals(404, r.getStatus());
        assertEquals("Referenced athlete not found", errorOf(r));
    }

    @Test
    void otherConstraint_23505_maps_to_400() {
        PersistenceException ex = new PersistenceException("dup",
                new SQLException("unique", "23505"));

        Response r = mappers.mapPersistence(ex);

        assertEquals(400, r.getStatus());
        assertEquals("Request violates a data constraint", errorOf(r));
    }

    @Test
    void checkConstraint_23514_maps_to_400() {
        PersistenceException ex = new PersistenceException("check",
                new SQLException("check", "23514"));

        Response r = mappers.mapPersistence(ex);

        assertEquals(400, r.getStatus());
        assertEquals("Request violates a data constraint", errorOf(r));
    }

    @Test
    void nonConstraintPersistence_maps_to_500_notMaskedAs4xx() {
        // No SQLException in the chain → not classifiable → genuine server error.
        Response r = mappers.mapPersistence(new PersistenceException("detached entity"));

        assertEquals(500, r.getStatus());
        assertEquals("Internal server error", errorOf(r));
    }

    @Test
    void sqlState_isFoundDeepInTheCauseChain() {
        PersistenceException ex = new PersistenceException("wrapper",
                new RuntimeException("hibernate", new SQLException("fk", "23503")));

        assertEquals(404, mappers.mapPersistence(ex).getStatus());
    }
}
