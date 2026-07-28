package com.nanobase.specai.compliance.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CompositeConditionEvaluator {
    public CompositeResult combine(String providerCode, List<String> childStatuses,
                                   Map<String, Object> configuration) {
        long satisfied = childStatuses.stream().filter("SATISFIED"::equals).count();
        long unresolved = childStatuses.stream().filter(status ->
            !"SATISFIED".equals(status) && !"NOT_SATISFIED".equals(status)).count();
        boolean result = switch (providerCode) {
            case "ALL" -> satisfied == childStatuses.size();
            case "ANY" -> satisfied > 0;
            case "NOT" -> satisfied == 0 && childStatuses.size() == 1;
            case "AT_LEAST_N" -> satisfied >= number(configuration.get("minimum"));
            default -> throw new IllegalArgumentException(
                "Unknown logical operator provider: " + providerCode);
        };
        String status = unresolved > 0 && !result ? "INDETERMINATE"
            : result ? "SATISFIED" : "NOT_SATISFIED";
        return new CompositeResult(status, childStatuses.size(), (int) satisfied,
            (int) unresolved);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue()
            : Integer.parseInt(String.valueOf(value));
    }

    public record CompositeResult(String status, int total, int satisfied,
                                  int unresolved) {
    }
}
