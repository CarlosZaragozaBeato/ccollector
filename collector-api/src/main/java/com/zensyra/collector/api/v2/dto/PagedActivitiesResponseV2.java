package com.zensyra.collector.api.v2.dto;

import com.zensyra.collector.api.dto.ActivityDto;
import com.zensyra.collector.query.model.QueryResult;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record PagedActivitiesResponseV2(
        List<ActivityDto> items,
        int page,
        int size,
        boolean partial,
        List<SourceFailureDto> failures
) {
    public static PagedActivitiesResponseV2 from(
            QueryResult<com.zensyra.collector.query.model.Activity> result, int page, int size) {
        return new PagedActivitiesResponseV2(
                result.data().stream().map(ActivityDto::from).toList(),
                page,
                size,
                result.isPartial(),
                result.failures().stream().map(SourceFailureDto::from).toList()
        );
    }
}
