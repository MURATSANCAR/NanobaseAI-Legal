package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.EvaluationMethod;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import com.nanobase.specai.analysis.domain.RequirementConditionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementConditionExtractor {
    private static final Pattern ISO = Pattern.compile(
        "\\b(ISO\\s*\\d{4,5}(?:[:-]?\\d{0,4})?|PCI\\s*DSS|SOC\\s*2|Tier\\s*[IVX0-9]+)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTANCE = Pattern.compile(
        "(?i)(?:en az|minimum|at least)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(km|kilometre|kilometer|m|metre)");
    private static final Pattern PERSONNEL = Pattern.compile(
        "(?i)(?:en az|minimum|at least)\\s*(\\d+)\\s*(?:personel|personnel|uzman|expert|engineer)");
    private static final Pattern EXPERIENCE = Pattern.compile(
        "(?i)(?:son|last)?\\s*(\\d+)\\s*(yıl|year|ay|month)");
    private static final Pattern SLA = Pattern.compile(
        "(?i)(?:sla|erişilebilirlik|availability)\\s*(?:en az|minimum|at least)?\\s*%?\\s*(\\d+(?:[.,]\\d+)?)\\s*%?");
    private static final Pattern RTO = Pattern.compile(
        "(?i)\\b(RTO|RPO)\\b[^\\d]{0,20}(?:en fazla|maximum|at most)?\\s*(\\d+)\\s*(saat|hour|dakika|minute|h|min)");

    private final RequirementConditionRepository conditions;
    private final Clock clock;

    @Autowired
    public RequirementConditionExtractor(RequirementConditionRepository conditions) {
        this(conditions, Clock.systemUTC());
    }

    RequirementConditionExtractor(RequirementConditionRepository conditions, Clock clock) {
        this.conditions = conditions;
        this.clock = clock;
    }

    public record ExtractionResult(List<RequirementCondition> conditions,
                                   EvaluationMethod fallbackMethod,
                                   boolean succeeded) {
    }

    @Transactional
    public ExtractionResult extractAndPersist(UUID organizationId, UUID requirementId,
                                              String requirementText, JsonNode candidate) {
        Instant now = clock.instant();
        List<RequirementCondition> extracted = new ArrayList<>();
        if (candidate != null && candidate.path("conditions").isArray()) {
            int sequence = 0;
            for (JsonNode node : candidate.path("conditions")) {
                RequirementCondition condition = fromJson(organizationId, requirementId, node,
                    sequence++, now);
                if (condition != null) {
                    extracted.add(condition);
                }
            }
        }
        if (extracted.isEmpty() && requirementText != null) {
            extracted.addAll(fromText(organizationId, requirementId, requirementText, now));
        }
        if (extracted.isEmpty()) {
            return new ExtractionResult(List.of(), EvaluationMethod.MANUAL_REVIEW, false);
        }
        conditions.saveAll(extracted);
        return new ExtractionResult(List.copyOf(extracted), null, true);
    }

    private RequirementCondition fromJson(UUID organizationId, UUID requirementId, JsonNode node,
                                          int sequence, Instant now) {
        ConditionOperator operator = parseOperator(node.path("operator").asText(null));
        if (operator == null) {
            return null;
        }
        BigDecimal numeric = null;
        if (node.path("expectedNumericValue").isNumber()) {
            numeric = node.path("expectedNumericValue").decimalValue();
        }
        LocalDate date = null;
        if (node.path("expectedDate").isTextual()) {
            try {
                date = LocalDate.parse(node.path("expectedDate").asText());
            } catch (Exception ignored) {
                date = null;
            }
        }
        Boolean bool = node.path("expectedBoolean").isMissingNode()
            || node.path("expectedBoolean").isNull()
            ? null : node.path("expectedBoolean").asBoolean();
        return RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
            node.path("conditionType").asText("GENERIC"),
            node.path("fieldName").asText(null),
            operator,
            node.path("expectedValue").asText(null),
            numeric,
            node.path("expectedUnit").asText(null),
            date,
            bool,
            sequence,
            !node.path("mandatory").isBoolean() || node.path("mandatory").asBoolean(),
            now);
    }

    private List<RequirementCondition> fromText(UUID organizationId, UUID requirementId,
                                                String text, Instant now) {
        List<RequirementCondition> result = new ArrayList<>();
        int sequence = 0;
        Matcher iso = ISO.matcher(text);
        while (iso.find()) {
            String value = iso.group(1).replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "CERTIFICATE", "CERTIFICATE", ConditionOperator.EXISTS, value, null, null, null,
                null, sequence++, true, now));
        }
        Matcher distance = DISTANCE.matcher(text);
        if (distance.find()) {
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "NUMERIC", "DATA_CENTER_DISTANCE", ConditionOperator.GREATER_THAN_OR_EQUAL,
                null, parseDecimal(distance.group(1)),
                distance.group(2).toLowerCase(Locale.ROOT).startsWith("m")
                    && !distance.group(2).toLowerCase(Locale.ROOT).startsWith("mi")
                    && !distance.group(2).toLowerCase(Locale.ROOT).startsWith("km")
                    ? "m" : "km",
                null, null, sequence++, true, now));
        }
        Matcher personnel = PERSONNEL.matcher(text);
        if (personnel.find()) {
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "NUMERIC", "PERSONNEL_COUNT", ConditionOperator.GREATER_THAN_OR_EQUAL,
                null, parseDecimal(personnel.group(1)), "count", null, null, sequence++, true, now));
        }
        Matcher experience = EXPERIENCE.matcher(text);
        if (experience.find()) {
            String unit = experience.group(2).toLowerCase(Locale.ROOT).startsWith("y")
                || experience.group(2).toLowerCase(Locale.ROOT).startsWith("year")
                ? "y" : "mo";
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "NUMERIC", "EXPERIENCE_DURATION", ConditionOperator.GREATER_THAN_OR_EQUAL,
                null, parseDecimal(experience.group(1)), unit, null, null, sequence++, true, now));
        }
        Matcher sla = SLA.matcher(text);
        if (sla.find()) {
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "NUMERIC", "SLA", ConditionOperator.GREATER_THAN_OR_EQUAL,
                null, parseDecimal(sla.group(1)), "%", null, null, sequence++, true, now));
        }
        Matcher rto = RTO.matcher(text);
        while (rto.find()) {
            String field = rto.group(1).toUpperCase(Locale.ROOT);
            String unitRaw = rto.group(3).toLowerCase(Locale.ROOT);
            String unit = unitRaw.startsWith("d") || unitRaw.startsWith("min") ? "min" : "h";
            result.add(RequirementCondition.create(UUID.randomUUID(), organizationId, requirementId,
                "NUMERIC", field, ConditionOperator.LESS_THAN_OR_EQUAL,
                null, parseDecimal(rto.group(2)), unit, null, null, sequence++, true, now));
        }
        if (text.toLowerCase(Locale.ROOT).contains("tier")) {
            Matcher tier = Pattern.compile("(?i)tier\\s*([ivx0-9]+|\\d+)").matcher(text);
            if (tier.find()) {
                result.add(RequirementCondition.create(UUID.randomUUID(), organizationId,
                    requirementId, "TEXT", "TIER", ConditionOperator.EQUALS,
                    "Tier " + tier.group(1).toUpperCase(Locale.ROOT), null, null, null, null,
                    sequence, true, now));
            }
        }
        return result;
    }

    private ConditionOperator parseOperator(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ConditionOperator.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        return new BigDecimal(raw.replace(',', '.'));
    }
}
