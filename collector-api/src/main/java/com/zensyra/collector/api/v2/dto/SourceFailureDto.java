package com.zensyra.collector.api.v2.dto;

import com.zensyra.collector.query.model.SourceFailure;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SourceFailureDto(String source, String reason) {
    public static SourceFailureDto from(SourceFailure failure) {
        return new SourceFailureDto(failure.sourceName(), failure.reason());
    }
}
