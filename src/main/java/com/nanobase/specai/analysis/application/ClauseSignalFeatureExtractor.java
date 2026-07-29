package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalContext;
import com.nanobase.specai.analysis.application.AnalysisModels.TerminologyMatch;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.document.domain.Clause;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

@Service
public class ClauseSignalFeatureExtractor {
    private static final Pattern NUMBER =
        Pattern.compile("(?<![\\p{L}\\p{N}])[-+]?\\d+(?:[.,]\\d+)?");
    /**
     * Obligation / requirement modality cues common in TR/EN tender specs.
     * Used as a lightweight semantic prior so body-bearing clauses reach EXTRACT
     * without depending on an empty terminology catalog.
     */
    private static final Pattern OBLIGATION = Pattern.compile(
        "(?iu)\\b("
            + "olmal[ıi]d[ıi]r|olmal[ıi]|zorunlu(?:dur)?|asgari|en\\s+az|"
            + "edecekti?r|edilecekti?r|yap[ıi]lacakt[ıi]r|sa[gğ]lanmal[ıi]d[ıi]r|"
            + "bulunmal[ıi]d[ıi]r|sunacakt[ıi]r|kar[sş][ıi]lanmal[ıi]d[ıi]r|"
            + "shall|must|required|mandatory|at\\s+least"
            + ")\\b");
    private static final int SUBSTANTIAL_BODY_CHARS = 80;
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
        String raw = clause.rawText() == null ? "" : clause.rawText();
        String title = clause.title() == null ? "" : clause.title().trim();
        boolean substantialBody = raw.trim().length() >= SUBSTANTIAL_BODY_CHARS
            && raw.trim().length() > title.length() + 20;

        double structuralParts = 0;
        double structuralChecks = 0;
        structuralChecks++;
        structuralParts += clause.clauseNumber() == null || clause.clauseNumber().isBlank() ? 0 : 1;
        structuralChecks++;
        structuralParts += title.isBlank() ? 0 : 1;
        structuralChecks++;
        structuralParts += (clause.parentId() != null || substantialBody) ? 1 : 0;

        double semantic = obligationScore(raw);

        Map<String, Double> signals = new HashMap<>();
        signals.put("terminology", terminology);
        signals.put("structure", structuralParts / structuralChecks);
        signals.put("numeric", NUMBER.matcher(raw).find() ? 1d : 0d);
        signals.put("semantic", semantic);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("terminologyReason", "APPROVED_CATALOG_MATCHES");
        metadata.put("structureReason", "DOCUMENT_STRUCTURE_FEATURES");
        metadata.put("numericReason", "NUMERIC_EXPRESSION_PRESENT");
        metadata.put("semanticReason", "OBLIGATION_MODALITY_HEURISTIC");
        metadata.put("substantialBody", substantialBody);
        metadata.put("terminologyMatches", matches.stream().map(TerminologyMatch::entryId).toList());
        return new ClauseSignalContext(profile.organizationId(), profile.policyVersionId(),
            clause, Map.copyOf(signals), Map.copyOf(metadata));
    }

    public List<TerminologyMatch> matches(Clause clause, AnalysisProfile profile) {
        return catalog.terminologyMatches(profile.organizationId(),
            terminologyIds(profile.terminologySetIdsJson()), clause.normalizedText());
    }

    static double obligationScore(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return 0d;
        }
        var matcher = OBLIGATION.matcher(rawText.toLowerCase(Locale.ROOT));
        int hits = 0;
        while (matcher.find()) {
            hits++;
            if (hits >= 3) {
                return 1d;
            }
        }
        if (hits == 0) {
            return 0d;
        }
        if (hits == 1) {
            return 0.85d;
        }
        return 1d;
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
