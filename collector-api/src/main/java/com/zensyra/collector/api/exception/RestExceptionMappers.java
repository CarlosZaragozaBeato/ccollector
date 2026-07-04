package com.zensyra.collector.api.exception;

import com.zensyra.collector.api.resource.ApiResponses;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.sql.SQLException;

/**
 * Global JAX-RS exception mappers for the read/write API. Translates persistence
 * and parameter-conversion failures into the shared {@link ApiResponses#error}
 * shape ({@code {"error": "..."}}) so no write endpoint leaks a raw 500 or a bare
 * framework 404. Lives in collector-api (REST layer) per ADR-001 — references
 * only jakarta.persistence and java.sql, never collector-strava.
 */
public class RestExceptionMappers {

    private static final Logger LOG = Logger.getLogger(RestExceptionMappers.class);

    // PostgreSQL SQLSTATE integrity-constraint classes (class 23).
    private static final String FK_VIOLATION = "23503";

    /**
     * Maps a persistence-layer constraint violation. A foreign-key violation
     * (an unknown {@code athleteId} referencing {@code athlete_profiles}) becomes
     * a 404; any other integrity constraint (unique/check/not-null, class 23)
     * becomes a 400. A persistence exception that is not a classifiable
     * constraint violation is a genuine server error and is left as a 500.
     */
    @ServerExceptionMapper
    public Response mapPersistence(PersistenceException exception) {
        String sqlState = findSqlState(exception);

        if (FK_VIOLATION.equals(sqlState)) {
            return ApiResponses.error(Response.Status.NOT_FOUND, "Referenced athlete not found");
        }
        if (sqlState != null && sqlState.startsWith("23")) {
            return ApiResponses.error(Response.Status.BAD_REQUEST, "Request violates a data constraint");
        }

        // Not a constraint violation — do not mask a real server error as 4xx.
        LOG.errorf(exception, "Unclassified persistence failure (sqlState=%s)", sqlState);
        return ApiResponses.error(Response.Status.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    /** Walks the cause chain for the first {@link SQLException} and returns its SQLSTATE. */
    private static String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
