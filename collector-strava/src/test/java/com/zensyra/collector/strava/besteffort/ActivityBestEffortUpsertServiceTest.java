package com.zensyra.collector.strava.besteffort;

import com.zensyra.collector.strava.activity.Activity;
import com.zensyra.collector.strava.api.dto.StravaBestEffortDto;
import com.zensyra.collector.strava.identity.StravaActivityIdentityService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@QuarkusTest
class ActivityBestEffortUpsertServiceTest {

    @InjectMock
    ActivityBestEffortRepository repository;

    @InjectMock
    StravaActivityIdentityService activityIdentityService;

    @Inject
    ActivityBestEffortUpsertService service;

    @Test
    void shouldPersistBestEfforts() {
        Long activityStravaId = 42L;
        when(repository.deleteByActivityStravaId(activityStravaId)).thenReturn(0L);

        StravaBestEffortDto dto = new StravaBestEffortDto();
        dto.setId(1L);
        dto.setName("1 mile");
        dto.setDistance(1609);
        dto.setElapsedTime(360);
        dto.setIsKom(false);
        dto.setPrRank(1);

        service.upsertBestEfforts(buildActivity(7L, activityStravaId), List.of(dto));

        ArgumentCaptor<ActivityBestEffort> captor = ArgumentCaptor.forClass(ActivityBestEffort.class);
        verify(repository).persist(captor.capture());
        verify(activityIdentityService).resolveOrCreateReference(7L, activityStravaId);

        ActivityBestEffort saved = captor.getValue();
        assertEquals(42L, saved.getActivityStravaId());
        assertEquals("1 mile", saved.getName());
        assertEquals(1609, saved.getDistance());
        assertEquals(360, saved.getElapsedTime());
        assertFalse(saved.getIsKom());
        assertEquals(1, saved.getPrRank());
    }

    @Test
    void shouldDeletePreviousBestEffortsBeforeInserting() {
        Long activityStravaId = 99L;
        when(repository.deleteByActivityStravaId(activityStravaId)).thenReturn(3L);

        StravaBestEffortDto dto = new StravaBestEffortDto();
        dto.setName("5k");
        dto.setDistance(5000);
        dto.setElapsedTime(1200);

        service.upsertBestEfforts(buildActivity(7L, activityStravaId), List.of(dto));

        verify(repository).deleteByActivityStravaId(activityStravaId);
        verify(repository).persist(any(ActivityBestEffort.class));
    }

    @Test
    void shouldSkipWhenListIsEmpty() {
        service.upsertBestEfforts(buildActivity(7L, 1L), List.of());

        verifyNoInteractions(repository);
        verifyNoInteractions(activityIdentityService);
    }

    @Test
    void shouldSkipEntriesWithNullName() {
        Long activityStravaId = 7L;
        when(repository.deleteByActivityStravaId(activityStravaId)).thenReturn(0L);

        StravaBestEffortDto noName = new StravaBestEffortDto();
        noName.setDistance(400);

        StravaBestEffortDto valid = new StravaBestEffortDto();
        valid.setName("400m");
        valid.setDistance(400);
        valid.setElapsedTime(75);

        service.upsertBestEfforts(buildActivity(7L, activityStravaId), List.of(noName, valid));

        verify(repository, times(1)).persist(any(ActivityBestEffort.class));
    }

    private Activity buildActivity(Long athleteStravaId, Long activityStravaId) {
        Activity activity = new Activity();
        activity.setAthleteId(athleteStravaId);
        activity.setStravaId(activityStravaId);
        return activity;
    }
}
