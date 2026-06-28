package com.zensyra.collector.query.model;

import java.util.UUID;

/**
 * Provider-neutral personal-best read-model: the athlete's own best time at
 * a standard distance, and where that performance ranks among their own
 * historical activities. Keyed by the canonical {@code ActivityId}
 * ({@code TrainingSession.id}) of the activity the effort was recorded in.
 *
 * <p><strong>Deliberately omits Strava's {@code isKom} field.</strong> A
 * personal best ({@code prRank} against the athlete's own history) and a
 * segment leaderboard placement ("King/Queen of the Mountain" — the fastest
 * recorded time on a specific stretch of road across Strava's entire user
 * base) are different kinds of fact. The first is the athlete's own
 * performance, portable across any source that can compute it. The second
 * is a social ranking within one specific platform's community of users on
 * one specific platform's segment database — it has no meaning outside
 * Strava and no equivalent concept has been defined for any other source.
 * It is not generalized to a renamed field (e.g. {@code isSegmentLeader}):
 * doing so would launder a source-specific social fact into something that
 * merely looks neutral, which is the same mistake as accepting an
 * unconstrained status string instead of a real enum. If segment
 * leaderboard data is ever needed by a consumer, it belongs in a
 * Strava-specific contract, not this one — see ADR-001.
 */
public record BestEffort(
        UUID activityId,
        String name,
        Integer distanceMeters,
        Integer elapsedTimeSecs,
        Integer personalRecordRank
) {
}
