package com.hotel.system.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();
    List<AuditLog> findTop500ByOrderByCreatedAtDesc();

    @Query("""
        select event from AuditLog event
        where (:actor is null or lower(event.actorUsername) like lower(concat('%', :actor, '%')))
          and (:action is null or lower(event.action) like lower(concat('%', :action, '%')))
          and (:fromInstant is null or event.createdAt >= :fromInstant)
          and (:toExclusive is null or event.createdAt < :toExclusive)
        order by event.createdAt desc
        """)
    List<AuditLog> searchEvents(@Param("actor") String actor,
                                @Param("action") String action,
                                @Param("fromInstant") Instant fromInstant,
                                @Param("toExclusive") Instant toExclusive,
                                Pageable pageable);
}
