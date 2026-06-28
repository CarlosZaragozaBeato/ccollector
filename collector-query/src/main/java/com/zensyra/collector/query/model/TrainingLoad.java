package com.zensyra.collector.query.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider-neutral daily training load snapshot for one athlete.
 *
 * <p>Unlike {@link BestEffort} or {@link AthleteStats}, no field here
 * required an omission or restructuring decision: CTL (chronic training
 * load), ATL (acute training load), TSB (training stress balance), and the
 * day's TSS (training stress score) are generic fatigue/form modeling
 * concepts (the Coggan/Banister model), not a Strava-specific schema —
 * they are computed by this system's own jobs from synced activity data,
 * not values Strava itself reports. A straight field-for-field translation
 * is correct here.
 */
public record TrainingLoad(
        UUID athleteId,
        LocalDate date,
        Double tssDay,
        Double ctl,
        Double atl,
        Double tsb
) {
}
