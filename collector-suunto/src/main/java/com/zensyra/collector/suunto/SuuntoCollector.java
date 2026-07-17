package com.zensyra.collector.suunto;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.suunto.job.SyncSuuntoWorkoutsJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Suunto's {@link DataCollector} — discovered automatically by the runner's
 * {@code JobRegistry} via CDI, exactly like {@code StravaCollector}. One job
 * for the workouts MVP; webhook drain and reconciliation jobs join this list
 * in future issues.
 */
@ApplicationScoped
public class SuuntoCollector implements DataCollector {

    @Inject
    SyncSuuntoWorkoutsJob syncSuuntoWorkoutsJob;

    @Override
    public IntegrationSource source() {
        return IntegrationSource.SUUNTO;
    }

    @Override
    public List<SyncJob> jobs() {
        return List.of(syncSuuntoWorkoutsJob);
    }
}
