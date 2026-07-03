package com.zensyra.collector.api.resource;

import com.zensyra.collector.journal.model.HealthEvent;
import com.zensyra.collector.journal.repository.HealthEventRepository;
import com.zensyra.collector.journal.service.HealthEventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real {@link HealthEventRepository} overlap query against H2 —
 * not mocked — covering every date-range case required by #32. Window under
 * test: [2025-06-10, 2025-06-20].
 */
@QuarkusTest
class HealthEventOverlapTest {

    private static final UUID ATHLETE = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final LocalDate FROM = LocalDate.of(2025, 6, 10);
    private static final LocalDate TO = LocalDate.of(2025, 6, 20);

    @Inject
    HealthEventService service;

    @Inject
    HealthEventRepository repository;

    @Test
    @Transactional
    void overlapReturnsSpanningAndOngoingEventsOnly() {
        // included
        service.create(ATHLETE, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30), "ILLNESS", "spans-window", null);
        service.create(ATHLETE, LocalDate.of(2025, 6, 5), null, "INJURY", "ongoing-started-before", null);
        service.create(ATHLETE, LocalDate.of(2025, 6, 5), LocalDate.of(2025, 6, 15), "OTHER", "starts-before-ends-inside", null);
        service.create(ATHLETE, LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 25), "OTHER", "starts-inside-ends-after", null);
        // excluded
        service.create(ATHLETE, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 20), "ILLNESS", "entirely-before", null);
        service.create(ATHLETE, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 10), "ILLNESS", "entirely-after", null);
        service.create(ATHLETE, LocalDate.of(2025, 7, 5), null, "INJURY", "ongoing-starts-after-window", null);

        List<HealthEvent> result = repository.findByAthleteIdAndDateRange(ATHLETE, FROM, TO);
        Set<String> titles = result.stream().map(HealthEvent::getTitle).collect(Collectors.toSet());

        assertEquals(Set.of(
                "spans-window",
                "ongoing-started-before",
                "starts-before-ends-inside",
                "starts-inside-ends-after"
        ), titles);
    }

    @Test
    @Transactional
    void resultsAreOrderedByStartDateAscending() {
        UUID athlete = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        service.create(athlete, LocalDate.of(2025, 6, 18), null, "OTHER", "later", null);
        service.create(athlete, LocalDate.of(2025, 6, 12), null, "OTHER", "earlier", null);

        List<HealthEvent> result = repository.findByAthleteIdAndDateRange(athlete, FROM, TO);

        assertEquals(List.of("earlier", "later"),
                result.stream().map(HealthEvent::getTitle).toList());
    }
}
