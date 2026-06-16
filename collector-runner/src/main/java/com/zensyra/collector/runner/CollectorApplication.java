package com.zensyra.collector.runner;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CollectorApplication {
    // Clase marcadora — fuerza la inicialización del contexto CDI
    // en el módulo runner y permite el descubrimiento de beans
    // de los módulos dependientes (core, strava).

    @Scheduled(cron = "0 0 0 1 1 ? 2099")
    void schedulerBootstrap() {
        // nunca se ejecuta
    }
}
