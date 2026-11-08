package com.hotel.system.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AuditService {
    private final AuditLogRepository logs;
    private final Clock clock;

    public AuditService(AuditLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    public void record(String actor, String action, String targetType, String targetId, String details) {
        String safeActor = (actor == null || actor.isBlank()) ? "SYSTEM" : actor;
        logs.save(new AuditLog(safeActor, action, targetType, targetId, details));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recentEvents() {
        return logs.findTop500ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> searchEvents(String actor, String action, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        String cleanActor = cleanFilter(actor);
        String cleanAction = cleanFilter(action);
        Instant fromInstant = from == null ? null : from.atStartOfDay(clock.getZone()).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return logs.searchEvents(cleanActor, cleanAction, fromInstant, toExclusive, PageRequest.of(0, 500));
    }

    private String cleanFilter(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 80) throw new IllegalArgumentException("Audit filters must be 80 characters or fewer.");
        return clean;
    }
}
