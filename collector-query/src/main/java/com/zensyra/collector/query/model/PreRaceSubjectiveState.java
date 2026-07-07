package com.zensyra.collector.query.model;

/**
 * How the athlete reported <em>feeling</em> in the taper week before a race —
 * an aggregate of the {@code TrainingDay} diary entries in the 7 days preceding
 * race day (see {@link RacePerformanceContext}). This is the subjective
 * counterpart to the objective CTL/ATL/TSB {@link TrainingLoadPoint}s: it
 * answers "how ready did I feel?" rather than "how loaded was I?".
 *
 * <p>Diary data is sparse and optional — a {@code TrainingDay} row exists only
 * when the athlete entered it — so, exactly like {@link TrainingLoadPoint}, a
 * data gap is represented <em>explicitly</em> rather than with a substituted
 * value that could be mistaken for a real "neutral" report:
 * <ul>
 *   <li>{@code available == true}: at least one diary entry in the window carried
 *       a perceived-effort value and/or a subjective state. The metrics reflect
 *       only the non-null values actually recorded.</li>
 *   <li>{@code available == false}: no usable diary entry in the window.
 *       {@code entryCount == 0} and both metrics are {@code null}. Note this is
 *       distinct from an athlete actually reporting {@code NEUTRAL} — that is a
 *       real value and is never synthesized to stand in for missing data.</li>
 * </ul>
 *
 * <p>{@code available} is therefore exactly {@code entryCount > 0}. Each metric
 * degrades independently: an entry with a perceived-effort but no subjective
 * state contributes only to {@code averagePerceivedEffort}, and vice versa.
 *
 * @param entryCount              number of diary entries in the window that
 *                                carried a usable signal (a perceived-effort
 *                                and/or a subjective state) — e.g. "3 of the 7
 *                                pre-race days were logged"
 * @param averagePerceivedEffort  mean of the non-null RPE (1–10) values in the
 *                                window; {@code null} if none were recorded
 * @param dominantSubjectiveState the most frequently reported subjective state
 *                                in the window (ties broken by the most recent
 *                                entry); {@code null} if no state was recorded.
 *                                One of the {@code SubjectiveState} enum names.
 * @param available               whether any usable diary signal was found
 */
public record PreRaceSubjectiveState(
        int entryCount,
        Double averagePerceivedEffort,
        String dominantSubjectiveState,
        boolean available
) {
    /** An explicit "no diary data in the window" value. */
    public static PreRaceSubjectiveState unavailable() {
        return new PreRaceSubjectiveState(0, null, null, false);
    }
}
