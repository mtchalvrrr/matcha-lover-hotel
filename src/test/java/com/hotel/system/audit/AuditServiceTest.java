package com.hotel.system.audit;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Manila"));

    @Test
    void appliesActorActionAndInclusiveLocalDateFilters() {
        when(logs.searchEvents(eq("booking-admin"), eq("reservation"), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        AuditService service = new AuditService(logs, clock);

        service.searchEvents(" booking-admin ", " RESERVATION ", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8));

        verify(logs).searchEvents(eq("booking-admin"), eq("RESERVATION"),
                eq(LocalDate.of(2026, 11, 1).atStartOfDay(clock.getZone()).toInstant()),
                eq(LocalDate.of(2026, 11, 9).atStartOfDay(clock.getZone()).toInstant()),
                any(Pageable.class));
    }

    @Test
    void rejectsReversedDateRange() {
        AuditService service = new AuditService(logs, clock);

        assertThrows(IllegalArgumentException.class, () -> service.searchEvents(
                null, null, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 8)));
        verifyNoInteractions(logs);
    }

    @Test
    void rejectsOverlongFilters() {
        AuditService service = new AuditService(logs, clock);

        assertThrows(IllegalArgumentException.class, () -> service.searchEvents("a".repeat(81),
                null, null, null));
        verifyNoInteractions(logs);
    }
}
