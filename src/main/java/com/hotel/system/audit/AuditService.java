package com.hotel.system.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class AuditService {
    private static final String GENESIS = "GENESIS";
    private final AuditLogRepository logs;
    private final Clock clock;
    private final String hmacKey;

    public AuditService(AuditLogRepository logs, Clock clock) {
        this(logs, clock, "");
    }

    @Autowired
    public AuditService(AuditLogRepository logs, Clock clock,
                        @Value("${audit.hmac-key:}") String hmacKey) {
        this.logs = logs;
        this.clock = clock;
        this.hmacKey = hmacKey == null ? "" : hmacKey.trim();
    }

    public void record(String actor, String action, String targetType, String targetId, String details) {
        String safeActor = (actor == null || actor.isBlank()) ? "SYSTEM" : actor;
        Instant createdAt = Instant.now(clock);
        String previousHash = logs.findTop1ByOrderByIdDesc()
                .map(AuditLog::getEventHash).filter(value -> value != null && !value.isBlank())
                .orElse(GENESIS);
        String eventHash = calculateHash(previousHash, safeActor, action, targetType, targetId, details, createdAt);
        logs.save(new AuditLog(safeActor, action, targetType, targetId, details,
                createdAt, previousHash, eventHash));
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

    @Transactional(readOnly = true)
    public IntegrityResult verifyIntegrity() {
        List<AuditLog> events = logs.findAllByOrderByIdAsc();
        String expectedPrevious = GENESIS;
        int checked = 0;
        for (AuditLog event : events) {
            // Existing rows created before hash chaining was enabled remain readable.
            if (event.getEventHash() == null || event.getPreviousHash() == null) continue;
            if (!Objects.equals(expectedPrevious, event.getPreviousHash())) {
                return new IntegrityResult(false, checked, event.getId(), !hmacKey.isBlank());
            }
            String expectedHash = calculateHash(event.getPreviousHash(), event.getActorUsername(),
                    event.getAction(), event.getTargetType(), event.getTargetId(),
                    event.getDetails(), event.getCreatedAt());
            if (!constantTimeEquals(expectedHash, event.getEventHash())) {
                return new IntegrityResult(false, checked, event.getId(), !hmacKey.isBlank());
            }
            expectedPrevious = event.getEventHash();
            checked++;
        }
        return new IntegrityResult(true, checked, null, !hmacKey.isBlank());
    }

    private String calculateHash(String previousHash, String actor, String action, String targetType,
                                 String targetId, String details, Instant createdAt) {
        String canonical = String.join("|", safe(previousHash), safe(actor), safe(action),
                safe(targetType), safe(targetId), safe(details), createdAt.toString());
        try {
            byte[] bytes;
            if (!hmacKey.isBlank()) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                bytes = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            } else {
                bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Audit integrity hashing is unavailable.", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private String safe(String value) { return value == null ? "" : value; }

    private String cleanFilter(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 80) throw new IllegalArgumentException("Audit filters must be 80 characters or fewer.");
        return clean;
    }

    public record IntegrityResult(boolean valid, int checkedEntries, Long firstInvalidEntryId,
                                  boolean hmacProtected) { }
}
