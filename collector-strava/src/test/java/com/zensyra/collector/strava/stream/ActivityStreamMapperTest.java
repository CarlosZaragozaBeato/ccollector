package com.zensyra.collector.strava.stream;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActivityStreamMapperTest {

    private final ActivityStreamMapper mapper = new ActivityStreamMapper();

    @Test
    void shouldMapFullStreams() {
        Activity activity = buildActivity();

        MappedActivityStreams result = mapper.map(activity, 7L, Map.of(
                "time", stream(JsonNodeFactory.instance.arrayNode().add(0).add(5)),
                "distance", stream(JsonNodeFactory.instance.arrayNode().add(10.5).add(25.5)),
                "latlng", stream(JsonNodeFactory.instance.arrayNode()
                        .add(JsonNodeFactory.instance.arrayNode().add(41.0).add(2.0))
                        .add(JsonNodeFactory.instance.arrayNode().add(41.1).add(2.1))),
                "altitude", stream(JsonNodeFactory.instance.arrayNode().add(500.0).add(501.5)),
                "heartrate", stream(JsonNodeFactory.instance.arrayNode().add(150).add(151)),
                "watts", stream(JsonNodeFactory.instance.arrayNode().add(250).add(255)),
                "cadence", stream(JsonNodeFactory.instance.arrayNode().add(86).add(87))
        ));

        assertEquals(StreamSyncStatus.SYNCED, result.status());
        assertEquals(2, result.rows().size());
        assertEquals(7L, result.rows().getFirst().getAthleteId());
        assertEquals(Instant.parse("2026-03-20T10:00:00Z"), result.rows().getFirst().getTime());
        assertEquals(Instant.parse("2026-03-20T10:00:05Z"), result.rows().get(1).getTime());
        assertEquals(41.1, result.rows().get(1).getLatitude());
        assertEquals(2.1, result.rows().get(1).getLongitude());
        assertEquals(255, result.rows().get(1).getWatts());
    }

    @Test
    void shouldHandleMissingOptionalStreams() {
        Activity activity = buildActivity();

        MappedActivityStreams result = mapper.map(activity, 7L, Map.of(
                "time", stream(JsonNodeFactory.instance.arrayNode().add(0).add(5)),
                "distance", stream(JsonNodeFactory.instance.arrayNode().add(10.5))
        ));

        assertEquals(StreamSyncStatus.PARTIAL, result.status());
        assertEquals(2, result.rows().size());
        assertEquals(10.5, result.rows().getFirst().getDistanceM());
        assertNull(result.rows().get(1).getDistanceM());
        assertNull(result.rows().getFirst().getLatitude());
        assertNull(result.rows().getFirst().getHeartrateBpm());
    }

    @Test
    void shouldCalculateTimestampsFromElapsedSeconds() {
        Activity activity = buildActivity();

        MappedActivityStreams result = mapper.map(activity, 7L, Map.of(
                "time", stream(JsonNodeFactory.instance.arrayNode().add(12))
        ));

        assertEquals(Instant.parse("2026-03-20T10:00:12Z"), result.rows().getFirst().getTime());
        assertEquals(12, result.rows().getFirst().getId().getElapsedSeconds());
    }

    private Activity buildActivity() {
        Activity activity = new Activity();
        activity.setId(99L);
        activity.setStartDate(Instant.parse("2026-03-20T10:00:00Z"));
        return activity;
    }

    private StravaActivityStreamDto stream(com.fasterxml.jackson.databind.JsonNode data) {
        StravaActivityStreamDto dto = new StravaActivityStreamDto();
        dto.setData(data);
        return dto;
    }
}
