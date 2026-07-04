package com.zensyra.collector.journal;

/**
 * Length limits for the journal entities' free-text fields. These are domain
 * constraints derived from the entity schema, kept here (not in the REST layer)
 * so any write path — API or future — shares one source of truth.
 */
public final class JournalFieldLimits {

    private JournalFieldLimits() {
    }

    /**
     * Max length for short text fields — {@code HealthEvent.title} and
     * {@code RaceResult.raceName}. Matches the {@code varchar(255)} columns in
     * migrations 037/038; keep in sync if those column widths change.
     */
    public static final int SHORT_TEXT_MAX = 255;

    /**
     * Max length for {@code notes} across {@code training_days},
     * {@code health_events}, and {@code race_results}. The columns are {@code TEXT}
     * (effectively unbounded); this is an application policy cap, generous for a
     * personal note yet far below the default request body-size limit.
     */
    public static final int NOTES_MAX = 5000;
}
