package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Mirrors Python {@code textambiguity.measurable_fields}: fill structured
 * measurement/operator/threshold/testCondition from free text when confidence
 * is high enough for auto-resolution.
 */
@Component
public class MeasurableFieldsEnricher {
    public static final double AUTO_RESOLVE_MIN_CONF = 0.85d;

    private static final Pattern QTY = Pattern.compile(
        "(?i)\\b(?:en\\s+az|minimum|at\\s+least|en\\s+fazla|maximum|at\\s+most|"
            + "en\\s+çok|en\\s+geç|en\\s+erken|equals?|eşit)\\b"
            + "[^\\d]{0,24}(\\d+(?:[.,]\\d+)?)\\s*"
            + "([A-Za-zÇĞİÖŞÜçğıöşü%][\\wÇĞİÖŞÜçğıöşü/%\\-]{0,40})?");
    private static final Pattern WITHIN = Pattern.compile(
        "(?i)(\\d+)\\s*(takvim\\s*günü|iş\\s*günü|gün|saat|dakika|"
            + "calendar\\s*days?|business\\s*days?|days?|hours?|minutes?)"
            + "[^\\n]{0,24}(?:içinde|within|dahilinde)"
            + "|(?:içinde|within|dahilinde)[^\\n]{0,12}"
            + "(\\d+)\\s*(takvim\\s*günü|iş\\s*günü|gün|saat|dakika|"
            + "calendar\\s*days?|business\\s*days?|days?|hours?|minutes?)");
    private static final Pattern PRESENCE = Pattern.compile(
        "(?i)\\b(uygun\\s+olacaktır|sağlanacaktır|destekleyecektir|bulunacaktır|"
            + "yapılacaktır|edilecektir|teslim\\s+edilecektir|"
            + "shall\\s+be\\s+(?:provided|supported|compliant)|"
            + "must\\s+be\\s+(?:provided|supported))\\b");
    private static final Pattern GTE = Pattern.compile(
        "(?i)\\b(?:en\\s+az|minimum|at\\s+least|≥|>=)\\b");
    private static final Pattern LTE = Pattern.compile(
        "(?i)\\b(?:en\\s+fazla|en\\s+çok|maximum|at\\s+most|en\\s+geç|≤|<=)\\b");
    private static final Pattern OBJECT = Pattern.compile(
        "(?i)\\b([A-Z]{2}[\\w\\-]*|[A-Za-zÇĞİÖŞÜçğıöşü]{3,}(?:\\s+[A-Za-zÇĞİÖŞÜçğıöşü]{3,})?)\\b");

    private final ObjectMapper mapper;

    public MeasurableFieldsEnricher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Extraction(String operator, String threshold, String measurement,
                             String testCondition, double confidence, String kind,
                             Map<String, Object> suggestedFields) {
        public boolean fieldsComplete() {
            return notBlank(operator) && notBlank(threshold)
                && notBlank(measurement) && notBlank(testCondition);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        public boolean autoResolvable() {
            return fieldsComplete() && confidence >= AUTO_RESOLVE_MIN_CONF;
        }
    }

    public record Enrichment(JsonNode attributes, Extraction extraction, boolean changed,
                             String priority, List<String> missingFields) {
    }

    public Extraction extract(String text) {
        String body = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (body.isBlank()) {
            return empty();
        }
        Matcher within = WITHIN.matcher(body);
        if (within.find()) {
            String n = within.group(1) != null ? within.group(1) : within.group(3);
            String unitRaw = within.group(2) != null ? within.group(2) : within.group(4);
            String unit = normalizeUnit(unitRaw);
            return extraction("<=", n, unit == null ? "süre" : unit,
                "within_" + (unit == null ? "duration" : unit), 0.92, "within");
        }
        Matcher qty = QTY.matcher(body);
        if (qty.find()) {
            String n = qty.group(1).replace(',', '.');
            String unit = normalizeUnit(qty.group(2));
            String measurement = unit;
            if (measurement == null || measurement.equals("adet") || measurement.equals("piece")) {
                String trail = body.substring(Math.min(body.length(), qty.end()),
                    Math.min(body.length(), qty.end() + 48));
                Matcher obj = OBJECT.matcher(trail);
                if (obj.find()) {
                    measurement = (unit == null ? "" : unit + " ") + obj.group(1).trim();
                } else if (unit != null) {
                    measurement = unit;
                } else {
                    measurement = "quantity";
                }
            }
            String span = body.substring(Math.max(0, qty.start() - 24), qty.end());
            return extraction(operatorFrom(span), n, measurement.trim(),
                "numeric_threshold", 0.90, "quantity");
        }
        if (PRESENCE.matcher(body).find()) {
            return extraction("==", "true", "presence", "qualitative_presence",
                0.55, "presence");
        }
        return empty();
    }

    public Enrichment enrich(String text, JsonNode attributes, String category,
                             String modality) {
        ObjectNode attrs = attributes == null || attributes.isNull()
            ? mapper.createObjectNode() : attributes.deepCopy();
        Extraction extracted = extract(text);
        boolean changed = false;
        if (extracted.autoResolvable()) {
            changed |= putIfBlank(attrs, "measurement", extracted.measurement());
            changed |= putIfBlank(attrs, "operator", extracted.operator());
            changed |= putIfBlank(attrs, "acceptanceThreshold", extracted.threshold());
            changed |= putIfBlank(attrs, "testCondition", extracted.testCondition());
            attrs.put("measurableConfidence", extracted.confidence());
            attrs.put("measurableKind", extracted.kind());
            attrs.put("ambiguityStatus", "RESOLVED_STRUCTURED");
        } else if (!"none".equals(extracted.kind())) {
            attrs.set("suggestedFields", mapper.valueToTree(extracted.suggestedFields()));
        }
        List<String> missing = missingFields(attrs);
        String priority = priorityFor(category, modality, missing, extracted);
        return new Enrichment(attrs, extracted, changed, priority, missing);
    }

    public String describe(List<String> missingFields, String priority,
                           Map<String, Object> suggestedFields) {
        String base = missingFields == null || missingFields.isEmpty()
            ? "structured_gap" : String.join(", ", missingFields);
        StringBuilder description = new StringBuilder();
        if (priority != null && !priority.isBlank()) {
            description.append('[').append(priority).append("] ");
        }
        description.append(base);
        if (suggestedFields != null && !suggestedFields.isEmpty()) {
            description.append(" | suggestedFields=").append(suggestedFields);
        }
        return description.toString();
    }

    private static List<String> missingFields(JsonNode attrs) {
        List<String> missing = new ArrayList<>();
        if (blank(attrs, "measurement")) {
            missing.add("missingMeasurement");
        }
        if (blank(attrs, "operator")) {
            missing.add("missingOperator");
        }
        if (blank(attrs, "testCondition")) {
            missing.add("missingTestCondition");
        }
        if (blank(attrs, "acceptanceThreshold")) {
            missing.add("missingAcceptanceThreshold");
        }
        return missing;
    }

    private static String priorityFor(String category, String modality,
                                      List<String> missing, Extraction extracted) {
        String cat = category == null ? "" : category.toUpperCase(Locale.ROOT);
        String mod = modality == null ? "" : modality.toUpperCase(Locale.ROOT);
        boolean techSec = cat.contains("TECH") || cat.contains("SECURITY") || cat.equals("SEC")
            || cat.isBlank();
        boolean must = mod.contains("MUST") || mod.contains("MANDATORY") || mod.contains("SHALL");
        if (techSec && must && missing.contains("missingAcceptanceThreshold")) {
            return "HIGH";
        }
        if ("presence".equals(extracted.kind()) || extracted.confidence() < 0.7d) {
            return must ? "MEDIUM" : "LOW";
        }
        if (!missing.isEmpty()) {
            return must ? "MEDIUM" : "LOW";
        }
        return "LOW";
    }

    private Extraction empty() {
        return extraction(null, null, null, null, 0d, "none");
    }

    private Extraction extraction(String operator, String threshold, String measurement,
                                  String testCondition, double confidence, String kind) {
        Map<String, Object> suggested = new LinkedHashMap<>();
        if (measurement != null) {
            suggested.put("measurement", measurement);
        }
        if (operator != null) {
            suggested.put("operator", operator);
        }
        if (threshold != null) {
            suggested.put("acceptanceThreshold", threshold);
        }
        if (testCondition != null) {
            suggested.put("testCondition", testCondition);
        }
        suggested.put("measurableConfidence", confidence);
        suggested.put("measurableKind", kind);
        return new Extraction(operator, threshold, measurement, testCondition,
            confidence, kind, Map.copyOf(suggested));
    }

    private static boolean putIfBlank(ObjectNode attrs, String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        JsonNode current = attrs.get(key);
        if (current != null && !current.isNull()
            && !(current.isTextual() && current.asText().isBlank())) {
            return false;
        }
        attrs.put(key, value);
        return true;
    }

    private static boolean blank(JsonNode attrs, String key) {
        JsonNode value = attrs.path(key);
        return value.isMissingNode() || value.isNull()
            || (value.isTextual() && value.asText().isBlank());
    }

    private static String operatorFrom(String span) {
        if (LTE.matcher(span).find()) {
            return "<=";
        }
        if (GTE.matcher(span).find()) {
            return ">=";
        }
        return ">=";
    }

    private static String normalizeUnit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String u = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        return switch (u) {
            case "adet", "piece", "pcs" -> "adet";
            case "gb" -> "GB";
            case "tb" -> "TB";
            case "gün", "gun", "day", "days" -> "gün";
            case "takvim_günü", "takvim_gunu", "calendar_day", "calendar_days" -> "takvim_günü";
            case "iş_günü", "is_gunu", "business_day", "business_days" -> "iş_günü";
            case "saat", "hour", "hours" -> "saat";
            case "dakika", "minute", "minutes" -> "dakika";
            case "%", "yüzde" -> "%";
            default -> u;
        };
    }
}
