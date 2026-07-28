package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DateValidityComparisonStrategy implements ComparisonStrategy {
    private final ObjectMapper mapper;

    public DateValidityComparisonStrategy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(ComparisonContext context) {
        return "date-validity".equals(context.providerCode());
    }

    @Override
    public ComparisonResult compare(ComparisonContext context) {
        Instant required = context.requiredDate() == null ? Instant.now()
            : context.requiredDate();
        boolean valid = context.evidenceValidUntil() != null
            && !context.evidenceValidUntil().isBefore(required);
        ObjectNode explanation = mapper.createObjectNode();
        explanation.put("requiredAt", required.toString());
        if (context.evidenceValidUntil() != null) {
            explanation.put("validUntil", context.evidenceValidUntil().toString());
        }
        return new ComparisonResult(context.providerCode(),
            valid ? "SATISFIED" : "NOT_SATISFIED", true, explanation,
            valid ? List.of() : List.of("EVIDENCE_EXPIRED_OR_UNDATED"));
    }
}
