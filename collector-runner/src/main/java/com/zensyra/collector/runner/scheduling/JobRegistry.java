package com.zensyra.collector.runner.scheduling;

import com.zensyra.collector.core.sync.DataCollector;
import com.zensyra.collector.core.sync.SyncJob;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JobRegistry {

    private static final Logger LOG = Logger.getLogger(JobRegistry.class);

    @Inject
    Instance<DataCollector> collectors;

    @Inject
    Scheduler scheduler;

    @Inject
    SyncJobExecutor executor;

    private int registeredJobCount;

    void onStart(@Observes StartupEvent event) {
        if (collectors.isUnsatisfied()) {
            LOG.warn("No DataCollectors found — el scheduler está operativo pero sin jobs registrados");
            return;
        }

        int totalJobs = 0;

        for (DataCollector collector : collectors) {
            for (SyncJob job : collector.jobs()) {
                registerJob(job);
                totalJobs++;
            }
        }

        this.registeredJobCount = totalJobs;
        LOG.infof("JobRegistry: %d job(s) registrados en el scheduler", totalJobs);
    }

    /**
     * Returns the number of jobs successfully registered at startup.
     *
     * @return registered job count
     */
    public int getRegisteredJobCount() {
        return registeredJobCount;
    }

    private void registerJob(SyncJob job) {
        scheduler.newJob(job.jobId())
                .setCron(job.cronExpression())
                .setTask(executionContext -> {
                    try {
                        executor.execute(job);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .schedule();

        LOG.infof("Job registrado — id: '%s', cron: '%s'", job.jobId(), job.cronExpression());
    }
}
