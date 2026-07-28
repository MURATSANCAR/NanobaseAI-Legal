package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingEvidence;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingInput;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LayeredGroundingValidator implements GroundingValidator {
    private static final Pattern NUMBER =
        Pattern.compile("(?<![\\p{L}\\p{N}])[-+]?\\d+(?:[.,]\\d+)?");

    @Override
    public GroundingResult validate(GroundingInput input) {
        if (input.sourceFragments() == null || input.sourceFragments().isEmpty()) {
            return new GroundingResult("UNGROUNDED", 0, List.of("sourceFragments"), List.of());
        }
        String source = input.sourceText() == null ? "" : input.sourceText();
        String normalizedSource = normalize(source);
        List<GroundingEvidence> evidence = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        int checks = 0;
        int supported = 0;

        for (int index = 0; index < input.sourceFragments().size(); index++) {
            String fragment = input.sourceFragments().get(index);
            checks++;
            if (fragment != null && !fragment.isBlank() && source.contains(fragment)) {
                supported++;
                evidence.add(new GroundingEvidence("EXACT_FRAGMENT", fragment, 1, Map.of()));
            } else if (fragment != null && !fragment.isBlank()
                && normalizedSource.contains(normalize(fragment))) {
                supported++;
                evidence.add(new GroundingEvidence("NORMALIZED_TEXT", fragment, 0.95, Map.of()));
            } else {
                unsupported.add("sourceFragments[" + index + "]");
            }
        }

        Map<String, String> numericValues = new LinkedHashMap<>();
        Set<String> excludedPaths = excludedPaths(input.sourceMetadata().get("excludedPaths"));
        collectNumbers("", input.extractedRequirement(), numericValues, excludedPaths);
        for (Map.Entry<String, String> numeric : numericValues.entrySet()) {
            checks++;
            if (containsNormalizedNumber(source, numeric.getValue())) {
                supported++;
                evidence.add(new GroundingEvidence("NUMBER_GROUNDING", numeric.getValue(), 1,
                    Map.of("path", numeric.getKey())));
            } else {
                unsupported.add(numeric.getKey());
            }
        }

        Object additional = input.sourceMetadata().get("groundingEvidence");
        if (additional instanceof List<?> provided) {
            for (Object item : provided) {
                evidence.add(new GroundingEvidence("PROVIDED_SOURCE_EVIDENCE",
                    String.valueOf(item), 1, Map.of()));
            }
        }

        double coverage = checks == 0 ? 0 : (double) supported / checks;
        String status = coverage == 1 ? "GROUNDED"
            : coverage == 0 ? "UNGROUNDED" : "PARTIALLY_GROUNDED";
        return new GroundingResult(status, coverage, List.copyOf(unsupported),
            List.copyOf(evidence));
    }

    private void collectNumbers(String path, JsonNode node, Map<String, String> result,
                                Set<String> excludedPaths) {
        if (node == null || node.isNull()) {
            return;
        }
        if (excludedPaths.contains(path)) {
            return;
        }
        if (node.isNumber()) {
            result.put(path, node.asText());
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectNumbers(path.isEmpty() ? field.getKey() : path + "." + field.getKey(),
                    field.getValue(), result, excludedPaths);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectNumbers(path + "[" + index + "]", node.get(index), result,
                    excludedPaths);
            }
        }
    }

    private Set<String> excludedPaths(Object value) {
        if (!(value instanceof List<?> paths)) {
            return Set.of();
        }
        return paths.stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean containsNormalizedNumber(String source, String expected) {
        String normalizedExpected = expected.replace(',', '.');
        Matcher matcher = NUMBER.matcher(source);
        while (matcher.find()) {
            if (matcher.group().replace(',', '.').equals(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
    }
}
