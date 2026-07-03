package com.zensyra.collector.api.resource;

import com.zensyra.collector.journal.model.RaceResult;
import com.zensyra.collector.journal.repository.RaceResultRepository;
import com.zensyra.collector.journal.service.RaceResultService;
import com.zensyra.collector.query.model.RaceResultSummary;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises the real {@link RaceResultRepository} range filter against H2 — not
 * mocked — plus the {@code linkedActivityId} round-trip (null and a valid UUID,
 * with no FK enforcement).
 */
@QuarkusTest
class RaceResultRepositoryTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31);

    @Inject
    RaceResultService service;

    @Inject
    RaceResultRepository repository;

    @Test
    @Transactional
    void rangeFilterReturnsInWindowOnlyAscendingByRaceDate() {
        UUID athlete = UUID.fromString("00000000-0000-0000-0000-00000000ab01");
        service.create(athlete, LocalDate.of(2025, 10, 5), "October 10K", 10000.0, null, null, null, null, null);
        service.create(athlete, LocalDate.of(2025, 3, 2), "March Half", 21097.5, null, null, null, null, null);
        service.create(athlete, LocalDate.of(2024, 12, 31), "Last-year race", 5000.0, null, null, null, null, null);
        service.create(athlete, LocalDate.of(2026, 1, 1), "Next-year race", 5000.0, null, null, null, null, null);

        List<RaceResult> result = repository.findByAthleteIdAndDateRange(athlete, FROM, TO);

        // only the two 2025 races, ascending by raceDate
        assertEquals(List.of("March Half", "October 10K"),
                result.stream().map(RaceResult::getRaceName).toList());
    }

    @Test
    @Transactional
    void linkedActivityIdRoundTripsAsNullAndAsUuidWithNoFk() {
        UUID athlete = UUID.fromString("00000000-0000-0000-0000-00000000ab02");
        // A UUID that references no existing activity anywhere — accepted because
        // there is no FK constraint (source-agnostic by design).
        UUID orphanActivity = UUID.fromString("99999999-9999-9999-9999-999999999999");

        RaceResultSummary linked = service.create(athlete, LocalDate.of(2025, 6, 1),
                "Linked race", 10000.0, null, null, null, null, orphanActivity);
        RaceResultSummary unlinked = service.create(athlete, LocalDate.of(2025, 6, 2),
                "Unlinked race", 10000.0, null, null, null, null, null);

        assertEquals(orphanActivity, repository.findById(linked.id()).getLinkedActivityId());
        assertNull(repository.findById(unlinked.id()).getLinkedActivityId());
    }
}
