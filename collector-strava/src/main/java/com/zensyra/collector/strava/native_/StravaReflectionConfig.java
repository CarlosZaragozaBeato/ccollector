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
 * Registers Strava DTOs for reflection in the native binary. Jackson needs
 * access to getters and setters at runtime; without this registration,
 * GraalVM removes them during AOT compilation.
 *
 * The package is named native_ (with an underscore) to avoid a conflict with
 * Java's native reserved word.
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
    // Empty class that only carries the annotation.
}
