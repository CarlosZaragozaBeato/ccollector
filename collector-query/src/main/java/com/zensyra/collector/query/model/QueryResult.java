package com.zensyra.collector.query.model;

import java.util.List;

/**
 * Wraps the outcome of a composed multi-source query: the data successfully
 * gathered, plus which registered sources (if any) failed to contribute.
 *
 * <p>{@code isPartial()} is {@code true} whenever at least one source
 * failed — even if other sources succeeded and {@code data} is non-empty.
 * A consumer needing only the data (the {@code /v1} contract, frozen as-is)
 * has no use for this type; it exists for {@code /v2}, which surfaces
 * partial-failure information explicitly rather than silently dropping it
 * or letting one source's failure take down the whole request.
 */
public record QueryResult<T>(List<T> data, List<SourceFailure> failures) {

    public static <T> QueryResult<T> complete(List<T> data) {
        return new QueryResult<>(data, List.of());
    }

    public boolean isPartial() {
        return !failures.isEmpty();
    }
}
