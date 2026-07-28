package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.workflow.application.NotificationChannelAdapter.NotificationMessage;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Pattern PLACEHOLDER =
        Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WorkflowConditionEngine conditions;
    private final NotificationPayloadSanitizer sanitizer;
    private final List<NotificationChannelAdapter> adapters;
    private final PlatformMetrics metrics;

    public NotificationService(JdbcTemplate jdbc, ObjectMapper mapper,
                               WorkflowConditionEngine conditions,
                               NotificationPayloadSanitizer sanitizer,
                               List<NotificationChannelAdapter> adapters,
                               PlatformMetrics metrics) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.conditions = conditions;
        this.sanitizer = sanitizer;
        this.adapters = List.copyOf(adapters);
        this.metrics = metrics;
    }

    @Transactional
    public List<UUID> dispatch(UUID organizationId, UUID eventId,
                               String eventConceptCode, JsonNode rawPayload) {
        ObjectNode safePayload = sanitizer.sanitize(rawPayload);
        Map<String, Object> variables = mapper.convertValue(safePayload,
            new TypeReference<Map<String, Object>>() { });
        List<Rule> rules = jdbc.query("""
            select r.id, r.condition_expression_json::text,
                   r.channel_policy_json::text, c.concept_code,
                   v.subject_template, v.body_template
              from notification_rule r
              join notification_template t on t.id = r.template_id
              join notification_template_version v on v.id = t.active_version_id
              join ontology_concept c on c.id = t.channel_concept_id
              join ontology_concept e on e.id = r.trigger_event_concept_id
             where r.active = true and e.concept_code = ?
            """, (result, row) -> new Rule(
                result.getObject(1, UUID.class), parse(result.getString(2)),
                parse(result.getString(3)), result.getString(4),
                result.getString(5), result.getString(6)), eventConceptCode);
        List<UUID> deliveries = new ArrayList<>();
        for (Rule rule : rules) {
            if (!conditions.evaluate(new WorkflowConditionContext(variables),
                rule.condition()).matched()) {
                continue;
            }
            for (JsonNode recipient : rule.channels().path("recipientReferences")) {
                UUID deliveryId = UUID.randomUUID();
                NotificationChannelAdapter adapter = adapters.stream()
                    .filter(candidate -> candidate.supports(rule.channelCode())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                        "No notification adapter for " + rule.channelCode()));
                NotificationMessage message = new NotificationMessage(recipient.asText(),
                    render(rule.subject(), safePayload), render(rule.body(), safePayload),
                    safePayload);
                var result = adapter.send(message);
                UUID status = concept(result.accepted()
                    ? "NOTIFICATION_SENT" : "NOTIFICATION_FAILED");
                jdbc.update("""
                    insert into notification_delivery (
                        id, organization_id, notification_rule_id, event_id,
                        recipient_reference, safe_payload_json, status_concept_id,
                        provider_message_id, error_code, created_at, sent_at
                    ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, now(),
                              case when ? then now() else null end)
                    """, deliveryId, organizationId, rule.id(), eventId,
                    recipient.asText(), json(safePayload), status,
                    result.providerMessageId(), result.errorCode(), result.accepted());
                metrics.sprint7(result.accepted()
                    ? "notification_sent_total" : "notification_failed_total");
                deliveries.add(deliveryId);
            }
        }
        return List.copyOf(deliveries);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inbox(String recipient) {
        return jdbc.queryForList("""
            select d.id, d.event_id as "eventId", d.safe_payload_json::text as payload,
                   c.concept_code as status, d.provider_message_id as "providerMessageId",
                   d.error_code as "errorCode", d.created_at as "createdAt",
                   d.sent_at as "sentAt"
              from notification_delivery d
              join ontology_concept c on c.id = d.status_concept_id
             where d.recipient_reference = ? order by d.created_at desc
            """, recipient);
    }

    private String render(String template, JsonNode safePayload) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            JsonNode value = safePayload.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                value == null ? "" : value.asText()));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private UUID concept(String code) {
        return jdbc.queryForObject("""
            select id from ontology_concept
             where concept_code = ? and concept_type = 'NOTIFICATION_STATUS'
             order by organization_id nulls last limit 1
            """, UUID.class, code);
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored notification JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Notification JSON cannot be serialized",
                exception);
        }
    }

    private record Rule(UUID id, JsonNode condition, JsonNode channels,
                        String channelCode, String subject, String body) {
    }
}
