package com.hotel.system.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class AuditService {
    private final AuditLogRepository logs;
    public AuditService(AuditLogRepository logs) { this.logs = logs; }

    public void record(String actor, String action, String targetType, String targetId, String details) {
        String safeActor = (actor == null || actor.isBlank()) ? "SYSTEM" : actor;
        logs.save(new AuditLog(safeActor, action, targetType, targetId, details));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recentEvents() { return logs.findTop500ByOrderByCreatedAtDesc(); }
}