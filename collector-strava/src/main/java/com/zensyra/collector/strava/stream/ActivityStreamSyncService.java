package com.zensyra.collector.strava.stream;

import com.zensyra.collector.core.exception.CollectorException;
import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.activity.ActivityRepository;
import com.zensyra.collector.strava.api.dto.StravaActivityStreamDto;
import com.zensyra.collector.strava.athlete.AthleteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class ActivityStreamSyncService {

    @Inject
    ActivityRepository activityRepository;

    @Inject
    AthleteRepository athleteRepository;

    @Inject
    ActivityStreamRepository activityStreamRepository;

    @Inject
    ActivityStreamMapper activityStreamMapper;

    @Transactional
    public void markRequested(Long activityStravaId, Instant requestedAt) {
        Activity activity = findRequiredActivity(activityStravaId);
        activity.setStreamsLastRequestedAt(requestedAt);
    }

    @Transactional
    public ActivityStreamSyncResult replaceStreams(Long activityStravaId, Map<String, StravaActivityStreamDto> streams) {
        Activity activity = findRequiredActivity(activityStravaId);
        Long athletePk = athleteRepository.findByStravaId(activity.getAthleteId())
                .map(a -> a.getId())
                .orElseThrow(() -> new CollectorException("Athlete not found for activity " + activityStravaId));

        MappedActivityStreams mappedStreams = activityStreamMapper.map(activity, athletePk, streams);

        activityStreamRepository.deleteByActivityId(activity.getId());
        for (ActivityStream row : mappedStreams.rows()) {
            activityStreamRepository.persist(row);
        }

        activity.setStreamsSyncStatus(mappedStreams.status());
        activity.setStreamsSyncedAt(Instant.now());
        activity.setStreamsSyncAttempts(0);
        activity.setStreamsLastError(null);

        return new ActivityStreamSyncResult(mappedStreams.status(), mappedStreams.rows().size());
    }

    @Transactional
    public void markFailure(Long activityStravaId, String errorMessage) {
        Activity activity = findRequiredActivity(activityStravaId);
        int attempts = activity.getStreamsSyncAttempts() != null ? activity.getStreamsSyncAttempts() : 0;
        activity.setStreamsSyncAttempts(attempts + 1);
        activity.setStreamsSyncStatus(StreamSyncStatus.FAILED);
        activity.setStreamsLastError(truncate(errorMessage));
    }

    private Activity findRequiredActivity(Long activityStravaId) {
        Activity activity = activityRepository.findByStravaId(activityStravaId)
                .orElseThrow(() -> new CollectorException("Activity not found for stravaId " + activityStravaId));
        if (activity.getStartDate() == null) {
            throw new CollectorException("Activity startDate is required for streams " + activityStravaId);
        }
        return activity;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }
}
