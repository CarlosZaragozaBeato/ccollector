package com.zensyra.collector.query.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provider-neutral activity metrics read-model. Keyed by the canonical
 * {@code ActivityId} ({@code TrainingSession.id}). No source-specific
 * identifier field exists on this type.
 */
public record ActivityMetrics(
        UUID activityId,
        BigDecimal normalizedPower,
        BigDecimal variabilityIndex,
        BigDecimal efficiencyFactor,
        BigDecimal intensityFactor
) {
}
