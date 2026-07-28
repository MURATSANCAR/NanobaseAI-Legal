package com.nanobase.specai.audit.api;

import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.application.AuditQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final AuditQueryService queries;

    public AuditController(AuditQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    Page<AuditResponse> list(@PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return queries.list(pageable)
            .map(AuditResponse::from);
    }

    record AuditResponse(UUID id, String userId, String eventType, String entityType,
                         UUID entityId, String beforeJson, String afterJson,
                         UUID correlationId, Instant createdAt) {
        static AuditResponse from(AuditEvent event) {
            return new AuditResponse(event.id(), event.userId(), event.eventType(),
                event.entityType(), event.entityId(), event.beforeJson(), event.afterJson(),
                event.correlationId(), event.createdAt());
        }
    }
}
