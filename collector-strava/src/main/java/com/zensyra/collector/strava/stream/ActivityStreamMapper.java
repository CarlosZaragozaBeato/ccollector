package com.zensyra.collector.strava.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ActivityStreamMapper {

    public MappedActivityStreams map(Activity activity, Long athleteId, Map<String, StravaActivityStreamDto> streams) {
        List<Integer> timeData = readIntegerArray(dataNode(streams, "time"));
        if (timeData.isEmpty()) {
            return new MappedActivityStreams(List.of(), StreamSyncStatus.PARTIAL);
        }

        List<Double> distanceData = readDoubleArray(dataNode(streams, "distance"));
        List<Double> altitudeData = readDoubleArray(dataNode(streams, "altitude"));
        List<Integer> heartrateData = readIntegerArray(dataNode(streams, "heartrate"));
        List<Integer> wattsData = readIntegerArray(dataNode(streams, "watts"));
        List<Integer> cadenceData = readIntegerArray(dataNode(streams, "cadence"));
        List<List<Double>> latlngData = readLatLngArray(dataNode(streams, "latlng"));

        boolean partial = distanceData.size() < timeData.size()
                || altitudeData.size() < timeData.size()
                || heartrateData.size() < timeData.size()
                || wattsData.size() < timeData.size()
                || cadenceData.size() < timeData.size()
                || latlngData.size() < timeData.size();

        List<ActivityStream> rows = new ArrayList<>(timeData.size());
        Instant startDate = activity.getStartDate();
        for (int i = 0; i < timeData.size(); i++) {
            Integer elapsedSeconds = timeData.get(i);
            if (elapsedSeconds == null) {
                partial = true;
                continue;
            }

            ActivityStream row = new ActivityStream();
            Instant sampleTime = startDate.plusSeconds(elapsedSeconds.longValue());
            row.setId(new ActivityStreamId(activity.getId(), sampleTime, elapsedSeconds));
            row.setAthleteId(athleteId);
            row.setTime(sampleTime);
            row.setDistanceM(valueAt(distanceData, i));
            row.setAltitudeM(valueAt(altitudeData, i));
            row.setHeartrateBpm(valueAt(heartrateData, i));
            row.setWatts(valueAt(wattsData, i));
            row.setCadenceRpm(valueAt(cadenceData, i));

            List<Double> latlng = valueAt(latlngData, i);
            if (latlng != null && latlng.size() >= 2) {
                row.setLatitude(latlng.get(0));
                row.setLongitude(latlng.get(1));
            } else if (latlng != null) {
                partial = true;
            }

            rows.add(row);
        }

        return new MappedActivityStreams(rows, partial ? StreamSyncStatus.PARTIAL : StreamSyncStatus.SYNCED);
    }

    private JsonNode dataNode(Map<String, StravaActivityStreamDto> streams, String type) {
        StravaActivityStreamDto dto = streams.get(type);
        return dto != null ? dto.getData() : null;
    }

    private List<Integer> readIntegerArray(JsonNode dataNode) {
        if (dataNode == null || !dataNode.isArray()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>(dataNode.size());
        for (JsonNode node : dataNode) {
            values.add(node.isNull() ? null : node.asInt());
        }
        return values;
    }

    private List<Double> readDoubleArray(JsonNode dataNode) {
        if (dataNode == null || !dataNode.isArray()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(dataNode.size());
        for (JsonNode node : dataNode) {
            values.add(node.isNull() ? null : node.asDouble());
        }
        return values;
    }

    private List<List<Double>> readLatLngArray(JsonNode dataNode) {
        if (dataNode == null || !dataNode.isArray()) {
            return List.of();
        }
        List<List<Double>> values = new ArrayList<>(dataNode.size());
        for (JsonNode node : dataNode) {
            if (!node.isArray() || node.size() < 2) {
                values.add(node.isArray() ? readDoubleArray(node) : null);
                continue;
            }
            values.add(List.of(node.get(0).asDouble(), node.get(1).asDouble()));
        }
        return values;
    }

    private <T> T valueAt(List<T> values, int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
