package com.zensyra.collector.suunto.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Faithful, source-specific representation of one workout as returned by
 * {@code GET /v2/workouts}. Field names taken verbatim from a real, complete
 * payload (2026-07-14) — every field visible in that payload is mapped;
 * ignoreUnknown remains only as defense against FUTURE fields Suunto may add.
 * Mapping to CCollector's neutral models is issue #6.
 *
 * <p>{@code startTime}/{@code stopTime}/{@code lastModified} are epoch
 * milliseconds; {@code recoveryTime}/{@code cumulativeRecoveryTime} seconds.
 * {@code tss} is the single method Suunto preferred for this workout;
 * {@code tssList} carries all computed methods (HR/POWER/PACE/MET) — #6's
 * method-selection logic consumes the list, not the single value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoWorkoutDto(
        Long workoutId,
        Integer activityId,
        Long startTime,
        Long stopTime,
        Double totalTime,
        Integer estimatedFloorsClimbed,
        Double totalDistance,
        Double totalAscent,
        Double totalDescent,
        SuuntoPositionDto startPosition,
        SuuntoPositionDto stopPosition,
        SuuntoPositionDto centerPosition,
        Double maxSpeed,
        Integer stepCount,
        Long recoveryTime,
        Long cumulativeRecoveryTime,
        SuuntoRankingsDto rankings,
        List<SuuntoWorkoutExtensionDto> extensions,
        List<String> extensionTypes,
        Double minAltitude,
        Double maxAltitude,
        Boolean isEdited,
        Boolean isManuallyAdded,
        SuuntoTssDto tss,
        List<SuuntoTssDto> tssList,
        List<String> suuntoTags,
        Double avgPower,
        Integer viewCount,
        Integer pictureCount,
        Integer commentCount,
        Double avgPace,
        Integer timeOffsetInMinutes,
        Integer energyConsumption,
        Double avgSpeed,
        SuuntoHrDataDto hrdata,
        SuuntoCadenceDto cadence,
        String workoutKey,
        Long lastModified
) {}
