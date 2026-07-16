package com.zensyra.collector.suunto.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zensyra.collector.core.credential.IntegrationCredential;
import com.zensyra.collector.core.credential.IntegrationCredentialRepository;
import com.zensyra.collector.core.oauth.OAuthToken;
import com.zensyra.collector.core.oauth.OAuthTokenRepository;
import com.zensyra.collector.core.oauth.OAuthTokenService;
import com.zensyra.collector.core.sync.IntegrationSource;
import com.zensyra.collector.core.sync.PartialJobFailureException;
import com.zensyra.collector.core.sync.SyncContext;
import com.zensyra.collector.suunto.api.SuuntoApiClient;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutDto;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListMetadata;
import com.zensyra.collector.suunto.api.dto.SuuntoWorkoutListResponse;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutRepository;
import com.zensyra.collector.suunto.workout.SuuntoWorkoutUpsertService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Three-state outcome coverage (#14) mirroring ComputeTrainingLoadJobTest,
 * plus the Suunto-specific behaviors: fail-fast on missing subscription key,
 * incremental watermark, offset pagination, and 429 aborting ALL remaining
 * athletes (per-deployment quota).
 */
@QuarkusTest
class SyncSuuntoWorkoutsJobTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    @InjectMock
    OAuthTokenRepository tokenRepository;

    @InjectMock
    OAuthTokenService tokenService;

    @InjectMock
    IntegrationCredentialRepository credentialRepository;

    @InjectMock
    SuuntoWorkoutRepository workoutRepository;

    @InjectMock
    SuuntoWorkoutUpsertService upsertService;

    @InjectMock
    @RestClient
    SuuntoApiClient apiClient;

    @Inject
    SyncSuuntoWorkoutsJob job;

    @BeforeEach
    void stubDefaults() {
        when(tokenService.getValidToken(any(IntegrationSource.class), anyString()))
                .thenReturn("access-token");
        stubSubscriptionKey("test-subscription-key");
        when(workoutRepository.findMaxLastModifiedByUser(anyString())).thenReturn(Optional.empty());
    }

    // --- full success ---

    @Test
    void allAthletesSucceed_jobCompletesNormally() {
        stubTokens("user-a");
        stubPage(page(dto("wk-1"), dto("wk-2")));

        assertDoesNotThrow(() -> job.execute(context()));

        verify(upsertService).upsert(eq("user-a"), argThat(d -> "wk-1".equals(d.workoutKey())));
        verify(upsertService).upsert(eq("user-a"), argThat(d -> "wk-2".equals(d.workoutKey())));
    }

    @Test
    void shouldSkipWhenNoTokens() {
        when(tokenRepository.findAllBySource(IntegrationSource.SUUNTO))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> job.execute(context()));

        verify(apiClient, never()).getWorkouts(any(), any(), any(), any(), any(), any(), any());
    }

    // --- partial failure ---

    @Test
    void partialFailure_remainingAthletesStillRun_throwsPartialException() {
        stubTokens("user-a", "user-b");
        stubPage(page(dto("wk-1")));
        doThrow(new RuntimeException("DB error")).when(upsertService).upsert(eq("user-a"), any());

        assertThrows(PartialJobFailureException.class, () -> job.execute(context()));

        // user-b was still attempted after user-a failed
        verify(upsertService).upsert(eq("user-b"), any());
    }

    // --- total failure ---

    @Test
    void allAthletesFail_jobThrowsTotalFailureNotPartial() {
        stubTokens("user-a", "user-b");
        stubPage(page(dto("wk-1")));
        doThrow(new RuntimeException("DB error")).when(upsertService).upsert(anyString(), any());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> job.execute(context()));

        assertFalse(thrown instanceof PartialJobFailureException,
                "all-failed must be a total failure, not a partial one");
    }

    @Test
    void missingSubscriptionKey_failsFastBeforeAnyApiCall() {
        stubTokens("user-a");
        when(credentialRepository.findBySource(IntegrationSource.SUUNTO)).thenReturn(Optional.empty());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> job.execute(context()));

        assertFalse(thrown instanceof PartialJobFailureException);
        verify(apiClient, never()).getWorkouts(any(), any(), any(), any(), any(), any(), any());
    }

    // --- rate limit: per-deployment quota aborts everyone ---

    @Test
    void http429AbortsAllRemainingAthletesWithoutFailingTheRun() {
        stubTokens("user-a", "user-b");
        when(apiClient.getWorkouts(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new WebApplicationException(429));

        // Early abort is neither success nor failure — the run ends quietly.
        assertDoesNotThrow(() -> job.execute(context()));

        // user-b must never be attempted: the quota belongs to the deployment.
        verify(apiClient, times(1)).getWorkouts(any(), any(), any(), any(), any(), any(), any());
    }

    // --- incremental watermark ---

    @Test
    void resumesFromStoredLastModifiedMinusSafetyMargin() {
        stubTokens("user-a");
        when(workoutRepository.findMaxLastModifiedByUser("user-a"))
                .thenReturn(Optional.of(1784011239246L));
        stubPage(page());

        job.execute(context());

        verify(apiClient).getWorkouts(
                eq("Bearer access-token"), eq("test-subscription-key"),
                eq(1784011239246L - DAY_MS), isNull(), eq(50), eq(0), isNull());
    }

    @Test
    void firstRunOmitsSinceEntirely_fullHistory() {
        stubTokens("user-a");
        stubPage(page(dto("wk-1")));

        job.execute(context());

        verify(apiClient).getWorkouts(
                eq("Bearer access-token"), eq("test-subscription-key"),
                isNull(), isNull(), eq(50), eq(0), isNull());
    }

    // --- pagination ---

    @Test
    void paginatesByOffsetUntilAShortPage() {
        stubTokens("user-a");
        List<SuuntoWorkoutDto> fullPage = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            fullPage.add(dto("wk-" + i));
        }
        when(apiClient.getWorkouts(any(), any(), any(), any(), eq(50), eq(0), any()))
                .thenReturn(page(fullPage.toArray(SuuntoWorkoutDto[]::new)));
        when(apiClient.getWorkouts(any(), any(), any(), any(), eq(50), eq(50), any()))
                .thenReturn(page(dto("wk-50"), dto("wk-51"), dto("wk-52")));

        assertDoesNotThrow(() -> job.execute(context()));

        verify(apiClient).getWorkouts(any(), any(), any(), any(), eq(50), eq(0), any());
        verify(apiClient).getWorkouts(any(), any(), any(), any(), eq(50), eq(50), any());
        verify(upsertService, times(53)).upsert(eq("user-a"), any());
    }

    // --- helpers ---

    private void stubTokens(String... externalUserIds) {
        List<OAuthToken> tokens = new ArrayList<>();
        for (String externalUserId : externalUserIds) {
            OAuthToken token = new OAuthToken();
            token.setSource(IntegrationSource.SUUNTO);
            token.setExternalUserId(externalUserId);
            token.setAccessToken("access-token");
            token.setRefreshToken("refresh-token");
            token.setExpiresAt(Instant.now().plusSeconds(3600));
            tokens.add(token);
        }
        when(tokenRepository.findAllBySource(IntegrationSource.SUUNTO)).thenReturn(tokens);
    }

    private void stubSubscriptionKey(String key) {
        IntegrationCredential credential = mock(IntegrationCredential.class);
        when(credential.getApiSubscriptionKey()).thenReturn(key);
        when(credentialRepository.findBySource(IntegrationSource.SUUNTO))
                .thenReturn(Optional.of(credential));
    }

    private void stubPage(SuuntoWorkoutListResponse response) {
        when(apiClient.getWorkouts(any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(response);
    }

    private SuuntoWorkoutListResponse page(SuuntoWorkoutDto... workouts) {
        return new SuuntoWorkoutListResponse(
                null, List.of(workouts),
                new SuuntoWorkoutListMetadata(String.valueOf(workouts.length), "0"));
    }

    private SuuntoWorkoutDto dto(String workoutKey) {
        try {
            return MAPPER.readValue("{ \"workoutKey\": \"" + workoutKey + "\" }", SuuntoWorkoutDto.class);
        } catch (Exception e) {
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }

    private SyncContext context() {
        return new SyncContext(
                "suunto.sync-workouts",
                IntegrationSource.SUUNTO,
                Instant.now(),
                null);
    }
}
