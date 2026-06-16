package com.zensyra.collector.strava.native_;

import com.zensyra.collector.strava.api.dto.StravaBestEffortDto;
import com.zensyra.collector.strava.api.dto.StravaActivityDetailDto;
import com.zensyra.collector.strava.api.dto.StravaActivityDto;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import com.zensyra.collector.strava.api.dto.StravaAthleteDto;
import com.zensyra.collector.strava.api.dto.StravaGearDto;
import com.zensyra.collector.strava.api.dto.StravaLapDto;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registra los DTOs de Strava para reflexión en el binario nativo.
 * Jackson necesita acceder a getters/setters en runtime — sin este
 * registro GraalVM los elimina durante la compilación AOT.
 *
 * El package se llama native_ (con guión bajo) para evitar conflicto
 * con la palabra reservada native de Java.
 */
@RegisterForReflection(targets = {
        StravaAthleteDto.class,
        StravaActivityDto.class,
        StravaActivityDetailDto.class,
        StravaActivityStreamDto.class,
        StravaGearDto.class,
        StravaLapDto.class,
        StravaBestEffortDto.class
})
public class StravaReflectionConfig {
    // Clase vacía — solo sirve como portador de la anotación
}
