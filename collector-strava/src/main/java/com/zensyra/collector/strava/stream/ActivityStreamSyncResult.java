package com.zensyra.collector.strava.stream;

public record ActivityStreamSyncResult(
        StreamSyncStatus status,
        int rowsWritten
) {
}
