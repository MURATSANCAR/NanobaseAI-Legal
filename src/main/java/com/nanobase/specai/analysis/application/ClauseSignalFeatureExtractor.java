package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalContext;
import com.nanobase.specai.analysis.application.AnalysisModels.TerminologyMatch;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.document.domain.Clause;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

@Service
public class ClauseSignalFeatureExtractor {
    private static final Pattern NUMBER =
        Pattern.compile("(?<![\\p{L}\\p{N}])[-+]?\\d+(?:[.,]\\d+)?");
    private final AnalysisCatalogPort catalog;
    private final ObjectMapper mapper;

    public ClauseSignalFeatureExtractor(AnalysisCatalogPort catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    public ClauseSignalContext extract(Clause clause, AnalysisProfile profile) {
        List<TerminologyMatch> matches = matches(clause, profile);
        double terminology = Math.min(1,
            matches.stream().mapToDouble(TerminologyMatch::weight).sum());
        double structuralParts = 0;
        double structuralChecks = 0;
        structuralChecks++;
        structuralParts += clause.clauseNumber() == null || clause.clauseNumber().isBlank() ? 0 : 1;
        structuralChecks++;
        structuralParts += clause.title() == null || clause.title().isBlank() ? 0 : 1;
        structuralChecks++;
        structuralParts += clause.parentId() == null ? 0 : 1;

        Map<String, Double> signals = new HashMap<>();
        signals.put("terminology", terminology);
        signals.put("structure", structuralParts / structuralChecks);
        signals.put("numeric", NUMBER.matcher(clause.rawText()).find() ? 1d : 0d);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("terminologyReason", "APPROVED_CATALOG_MATCHES");
        metadata.put("structureReason", "DOCUMENT_STRUCTURE_FEATURES");
        metadata.put("numericReason", "NUMERIC_EXPRESSION_PRESENT");
        metadata.put("terminologyMatches", matches.stream().map(TerminologyMatch::entryId).toList());
        return new ClauseSignalContext(profile.organizationId(), profile.policyVersionId(),
            clause, Map.copyOf(signals), Map.copyOf(metadata));
    }

    public List<TerminologyMatch> matches(Clause clause, AnalysisProfile profile) {
        return catalog.terminologyMatches(profile.organizationId(),
            terminologyIds(profile.terminologySetIdsJson()), clause.normalizedText());
    }

    private List<UUID> terminologyIds(String value) {
        try {
            return StreamSupport.stream(mapper.readTree(value).spliterator(), false)
                .map(node -> UUID.fromString(node.asText())).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Analysis profile terminology snapshot is invalid",
                exception);
        }
    }
}
