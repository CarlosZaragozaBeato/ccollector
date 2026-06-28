package com.zensyra.collector.query.model;

/**
 * Records that one registered source failed to contribute to a composed
 * query, without leaking internal exception details (stack traces, driver
 * messages) to API consumers. {@code sourceName} identifies which adapter
 * failed (e.g. the implementing class's simple name); {@code reason} is a
 * short, human-readable description safe to expose externally.
 */
public record SourceFailure(String sourceName, String reason) {
}
