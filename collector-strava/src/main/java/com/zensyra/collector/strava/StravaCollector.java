package com.zensyra.collector.strava;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.SyncJob;
import com.zensyra.collector.strava.job.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class StravaCollector implements DataCollector {

    @Inject SyncAthleteJob syncAthleteJob;
    @Inject SyncAthleteZonesJob syncAthleteZonesJob;
    @Inject SyncAthleteStatsJob syncAthleteStatsJob;
    @Inject SyncActivitiesJob syncActivitiesJob;
    @Inject SyncGearJob syncGearJob;
    @Inject SyncActivityDetailJob syncActivityDetailJob;
    @Inject SyncActivityStreamsJob syncActivityStreamsJob;
    @Inject InitialHistoricalSyncJob initialHistoricalSyncJob;
    @Inject ComputeTrainingLoadJob computeTrainingLoadJob;
    @Inject ComputeActivityMetricsJob computeActivityMetricsJob;
    @Inject BackfillTrainingLoadJob backfillTrainingLoadJob;
    @Inject SyncRoutesJob syncRoutesJob;

    @Override
    public IntegrationSource source() {
        return IntegrationSource.STRAVA;
    }

    @Override
    public List<SyncJob> jobs() {
        return List.of(
                syncAthleteJob,
                syncAthleteZonesJob,
                syncAthleteStatsJob,
                syncActivitiesJob,
                syncGearJob,
                syncActivityDetailJob,
                syncActivityStreamsJob,
                initialHistoricalSyncJob,
                computeTrainingLoadJob,
                computeActivityMetricsJob,
                backfillTrainingLoadJob,
                syncRoutesJob
        );
    }
}
