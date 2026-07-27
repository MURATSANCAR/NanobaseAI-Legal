package com.nanobase.specai.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final CurrentTenant currentTenant;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditEventRepository events, CurrentTenant currentTenant,
                        ObjectMapper objectMapper) {
        this(events, currentTenant, objectMapper, Clock.systemUTC());
    }

    AuditService(AuditEventRepository events, CurrentTenant currentTenant,
                 ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.currentTenant = currentTenant;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void record(String eventType, String entityType, UUID entityId,
                       Object before, Object after) {
        TenantPrincipal principal = currentTenant.require();
        recordSystem(principal.tenantId(), principal.subject(), eventType, entityType,
            entityId, before, after);
    }

    public void recordSystem(UUID organizationId, String actor, String eventType,
                             String entityType, UUID entityId, Object before, Object after) {
        RequestContext.RequestMetadata request = RequestContext.current();
        events.save(new AuditEvent(UUID.randomUUID(), organizationId, actor, eventType,
            entityType, entityId, request.ipAddress(), request.userAgent(),
            jsonOrNull(before), json(after), request.correlationId(), clock.instant()));
    }

    private String jsonOrNull(Object value) {
        return value == null ? null : json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit payload cannot be serialized", exception);
        }
    }
}
