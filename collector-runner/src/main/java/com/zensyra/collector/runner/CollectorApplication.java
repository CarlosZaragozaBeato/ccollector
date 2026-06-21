package com.zensyra.collector.runner;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CollectorApplication {
    // Marker class: initializes the CDI context in the runner module and enables
    // discovery of beans from dependent modules (core and Strava).

    @Scheduled(cron = "0 0 0 1 1 ? 2099")
    void schedulerBootstrap() {
        // never executed
    }
}
