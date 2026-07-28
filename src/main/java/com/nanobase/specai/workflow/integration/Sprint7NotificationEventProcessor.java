package com.nanobase.specai.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import com.nanobase.specai.workflow.application.NotificationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Sprint7NotificationEventProcessor {
    private final TenantDatabaseContext tenantDatabase;
    private final NotificationService notifications;

    public Sprint7NotificationEventProcessor(TenantDatabaseContext tenantDatabase,
                                             NotificationService notifications) {
        this.tenantDatabase = tenantDatabase;
        this.notifications = notifications;
    }

    @Transactional
    public void process(UUID organizationId, UUID eventId,
                        String eventType, JsonNode payload) {
        tenantDatabase.apply(organizationId);
        notifications.dispatch(organizationId, eventId, eventType, payload);
    }
}
