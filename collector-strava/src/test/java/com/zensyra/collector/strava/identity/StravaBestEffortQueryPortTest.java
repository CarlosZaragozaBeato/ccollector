package com.zensyra.collector.strava.identity;

import com.zensyra.collector.core.identity.IntegrationAccount;
import com.zensyra.collector.core.identity.IntegrationAccountRepository;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.query.model.BestEffort;
import com.zensyra.collector.strava.besteffort.ActivityBestEffort;
import com.zensyra.collector.strava.besteffort.ActivityBestEffortRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaBestEffortQueryPortTest {

    @Test
    void shouldReturnEmptyListWhenAthleteHasNoStravaAccount() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        ActivityBestEffortRepository activityBestEffortRepository = mock(ActivityBestEffortRepository.class);
        ActivityReferenceResolver activityReferenceResolver = mock(ActivityReferenceResolver.class);
        StravaBestEffortQueryPort port = new StravaBestEffortQueryPort(
                integrationAccountRepository, activityBestEffortRepository, activityReferenceResolver);

        UUID athleteId = UUID.randomUUID();
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of());

        List<BestEffort> result = port.listTopByAthlete(athleteId, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipEffortsWithoutACanonicalActivityReference() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        ActivityBestEffortRepository activityBestEffortRepository = mock(ActivityBestEffortRepository.class);
        ActivityReferenceResolver activityReferenceResolver = mock(ActivityReferenceResolver.class);
        StravaBestEffortQueryPort port = new StravaBestEffortQueryPort(
                integrationAccountRepository, activityBestEffortRepository, activityReferenceResolver);

        UUID athleteId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));

        ActivityBestEffort orphanEffort = buildEffort(9001L, "10k", 10000, 2400, true, 1);
        when(activityBestEffortRepository.findTopPrsByAthleteId(anyLong(), anyInt()))
                .thenReturn(List.of(orphanEffort));
        when(activityReferenceResolver.resolveCanonicalActivityId(eq(account.getId()), eq("9001")))
                .thenReturn(null);

        List<BestEffort> result = port.listTopByAthlete(athleteId, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnBestEffortsWithCanonicalActivityIdAndNoKomField() {
        IntegrationAccountRepository integrationAccountRepository = mock(IntegrationAccountRepository.class);
        ActivityBestEffortRepository activityBestEffortRepository = mock(ActivityBestEffortRepository.class);
        ActivityReferenceResolver activityReferenceResolver = mock(ActivityReferenceResolver.class);
        StravaBestEffortQueryPort port = new StravaBestEffortQueryPort(
                integrationAccountRepository, activityBestEffortRepository, activityReferenceResolver);

        UUID athleteId = UUID.randomUUID();
        UUID canonicalActivityId = UUID.randomUUID();
        IntegrationAccount account = new IntegrationAccount(athleteId, IntegrationSource.STRAVA, "111");
        when(integrationAccountRepository.findByAthleteId(athleteId)).thenReturn(List.of(account));

        // isKom=true on the Strava row — must never surface on the read-model.
        ActivityBestEffort stravaEffort = buildEffort(9001L, "10k", 10000, 2400, true, 1);
        when(activityBestEffortRepository.findTopPrsByAthleteId(eq(111L), eq(5)))
                .thenReturn(List.of(stravaEffort));
        when(activityReferenceResolver.resolveCanonicalActivityId(eq(account.getId()), eq("9001")))
                .thenReturn(canonicalActivityId);

        List<BestEffort> result = port.listTopByAthlete(athleteId, 5);

        assertEquals(1, result.size());
        BestEffort bestEffort = result.get(0);
        assertEquals(canonicalActivityId, bestEffort.activityId());
        assertEquals("10k", bestEffort.name());
        assertEquals(10000, bestEffort.distanceMeters());
        assertEquals(2400, bestEffort.elapsedTimeSecs());
        assertEquals(1, bestEffort.personalRecordRank());
        // BestEffort has no isKom-shaped field at all — there is nothing
        // further to assert here beyond the record's declared components,
        // which is the point: the type itself cannot carry that fact.
    }

    private ActivityBestEffort buildEffort(
            Long activityStravaId, String name, Integer distance, Integer elapsedTime,
            Boolean isKom, Integer prRank) {
        ActivityBestEffort effort = new ActivityBestEffort();
        effort.setActivityStravaId(activityStravaId);
        effort.setName(name);
        effort.setDistance(distance);
        effort.setElapsedTime(elapsedTime);
        effort.setIsKom(isKom);
        effort.setPrRank(prRank);
        return effort;
    }
}
