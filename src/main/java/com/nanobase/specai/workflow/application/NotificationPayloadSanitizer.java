package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadSanitizer {
    private static final Set<String> SAFE_FIELDS = Set.of(
        "projectName", "taskCode", "taskTitle", "riskSeverity", "dueAt",
        "applicationPath", "correlationId", "eventType");

    public ObjectNode sanitize(JsonNode input) {
        ObjectNode safe = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        SAFE_FIELDS.forEach(field -> {
            if (input != null && input.has(field) && input.get(field).isValueNode()) {
                safe.set(field, input.get(field));
            }
        });
        return safe;
    }
}
