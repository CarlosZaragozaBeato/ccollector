package com.zensyra.collector.strava.stream;

import java.util.List;

public record MappedActivityStreams(
        List<ActivityStream> rows,
        StreamSyncStatus status
) {
}
