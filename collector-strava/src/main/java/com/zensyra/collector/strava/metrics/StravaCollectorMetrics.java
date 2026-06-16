package com.zensyra.collector.strava.metrics;

import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.strava.ratelimit.StravaRateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Central registry of Micrometer counters, timers and gauges for the
 * Strava collector domain.
 */
@ApplicationScoped
public final class StravaCollectorMetrics {

    /** Counts activities successfully synchronised. */
    private final Counter activitiesSynced;

    /** Counts activities that failed to synchronise. */
    private final Counter activitiesFailed;

    /** Counts individual laps synchronised. */
    private final Counter lapsSynced;

    /** Counts HTTP 429 rate-limit responses from Strava. */
    private final Counter rateLimitHits;

    /** Times the execution of SyncActivityDetailJob per activity. */
    private final Timer activityDetailTimer;

    private final Counter activityStreamsSynced;
    private final Counter activityStreamRowsWritten;
    private final Counter activityStreamSyncErrors;
    private final Timer activityStreamSyncTimer;
    private final MeterRegistry registry;
    private final com.zensyra.collector.strava.activity.ActivityRepository activityRepository;
    private final OAuthTokenRepository tokenRepository;

    /**
     * Builds all meters and registers them with the global registry.
     *
     * @param registry    the Micrometer meter registry
     * @param rateLimiter the Strava rate limiter (used for the gauge)
     */
    @Inject
    public StravaCollectorMetrics(
            final MeterRegistry registry,
            final StravaRateLimiter rateLimiter,
            final com.zensyra.collector.strava.activity.ActivityRepository activityRepository,
            final OAuthTokenRepository tokenRepository) {

        this.registry = registry;
        this.activityRepository = activityRepository;
        this.tokenRepository = tokenRepository;

        this.activitiesSynced = Counter
            .builder("strava.activities.synced")
            .description("Actividades sincronizadas correctamente")
            .register(registry);
        this.activitiesFailed = Counter
            .builder("strava.activities.failed")
            .description("Actividades que fallaron al sincronizar")
            .register(registry);
        this.lapsSynced = Counter
            .builder("strava.laps.synced")
            .description("Vueltas sincronizadas")
            .register(registry);
        this.rateLimitHits = Counter
            .builder("strava.rate_limit.hits")
            .description("Veces que Strava devolvio 429")
            .register(registry);
        this.activityDetailTimer = Timer
            .builder("strava.activity_detail.duration")
            .description("Tiempo de ejecucion de SyncActivityDetailJob")
            .register(registry);
        this.activityStreamsSynced = Counter
            .builder("strava.activity_streams.synced")
            .description("Actividades con streams sincronizados")
            .register(registry);
        this.activityStreamRowsWritten = Counter
            .builder("strava.activity_stream_rows.written")
            .description("Filas de streams persistidas")
            .register(registry);
        this.activityStreamSyncErrors = Counter
            .builder("strava.activity_streams.errors")
            .description("Errores sincronizando streams")
            .register(registry);
        this.activityStreamSyncTimer = Timer
            .builder("strava.activity_stream_sync.duration")
            .description("Tiempo de sincronizacion de streams por actividad")
            .register(registry);

        Gauge.builder("strava.rate_limiter.available_permits", rateLimiter,
                rl -> (double) rl.availablePermits())
            .description("Permisos disponibles en el rate limiter de Strava")
            .register(registry);
    }

    /**
     * Forces eager bean instantiation at startup so all meters
     * are registered in Prometheus from the first scrape.
     *
     * @param ev the startup event (unused)
     */
    void onStart(@Observes final StartupEvent ev) {
        tokenRepository.findAllBySource(IntegrationSource.STRAVA).forEach(token -> {
            long athleteId = Long.parseLong(token.getExternalUserId());
            Gauge.builder("strava.activity_streams.backlog", activityRepository,
                    repository -> (double) repository.countPendingStreamActivities(athleteId, 3))
                .description("Actividades pendientes de sincronizar streams")
                .tag("athlete_id", token.getExternalUserId())
                .register(registry);
        });
    }

    /** Increments the successfully-synced activities counter. */
    public void incrementActivitiesSynced() {
        activitiesSynced.increment();
    }

    /** Increments the failed-activities counter. */
    public void incrementActivitiesFailed() {
        activitiesFailed.increment();
    }

    /**
     * Increments the laps-synced counter by the given amount.
     *
     * @param count number of laps to add
     */
    public void incrementLapsSynced(final int count) {
        lapsSynced.increment(count);
    }

    /** Increments the Strava rate-limit-hit counter. */
    public void incrementRateLimitHits() {
        rateLimitHits.increment();
    }

    /**
     * Starts a timer sample for an activity-detail fetch.
     *
     * @return a started Timer.Sample
     */
    public Timer.Sample startActivityDetailTimer() {
        return Timer.start();
    }

    /**
     * Stops the given sample and records its duration.
     *
     * @param sample the previously started timer sample
     */
    public void stopActivityDetailTimer(final Timer.Sample sample) {
        sample.stop(activityDetailTimer);
    }

    public void incrementActivityStreamsSynced() {
        activityStreamsSynced.increment();
    }

    public void incrementActivityStreamRowsWritten(int count) {
        activityStreamRowsWritten.increment(count);
    }

    public void incrementActivityStreamSyncErrors() {
        activityStreamSyncErrors.increment();
    }

    public Timer.Sample startActivityStreamSyncTimer() {
        return Timer.start(registry);
    }

    public void stopActivityStreamSyncTimer(Timer.Sample sample) {
        sample.stop(activityStreamSyncTimer);
    }
}
