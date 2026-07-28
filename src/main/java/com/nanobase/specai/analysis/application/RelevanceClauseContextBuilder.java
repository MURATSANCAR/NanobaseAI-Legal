package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ContextItem;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.DocumentTable;
import com.nanobase.specai.document.domain.DocumentTableRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RelevanceClauseContextBuilder implements ClauseContextBuilder {
    private final ClauseRepository clauses;
    private final DocumentTableRepository tables;
    private final AnalysisCatalogPort catalog;

    public RelevanceClauseContextBuilder(ClauseRepository clauses,
                                         DocumentTableRepository tables,
                                         AnalysisCatalogPort catalog) {
        this.clauses = clauses;
        this.tables = tables;
        this.catalog = catalog;
    }

    @Override
    public ClauseAnalysisContext build(Clause clause, AnalysisProfile profile) {
        PolicyConfiguration policy = new PolicyConfiguration(
            catalog.policy(profile.organizationId(), profile.policyVersionId()).configuration());
        int limit = policy.requiredInteger("context.maximumItems");
        double minimum = policy.requiredNumber("context.minimumRelevance");
        Map<String, Double> weights =
            policy.requiredWeights("context.relevanceWeights");

        List<ContextItem> candidates = new ArrayList<>();
        List<Clause> documentClauses =
            clauses.findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
                clause.documentVersionId(), profile.organizationId());
        for (Clause candidate : documentClauses) {
            if (candidate.id().equals(clause.id())) {
                continue;
            }
            double lexical = jaccard(tokens(clause.normalizedText()),
                tokens(candidate.normalizedText()));
            boolean family = candidate.id().equals(clause.parentId())
                || clause.id().equals(candidate.parentId())
                || (clause.parentId() != null
                    && clause.parentId().equals(candidate.parentId()));
            double structural = family ? 1 : 0;
            int distance = Math.abs(clause.pageStart() - candidate.pageStart());
            double pageProximity = 1d / (1d + distance);
            double relevance = lexical * requiredWeight(weights, "lexical")
                + structural * requiredWeight(weights, "structural")
                + pageProximity * requiredWeight(weights, "pageProximity");
            if (relevance >= minimum) {
                candidates.add(new ContextItem("CLAUSE", candidate.id(),
                    candidate.rawText(), relevance,
                    Map.of("pageStart", candidate.pageStart(),
                        "structuralFamily", family)));
            }
        }

        for (DocumentTable table : tables.findAllByDocumentVersionIdAndOrganizationId(
            clause.documentVersionId(), profile.organizationId(), Pageable.unpaged())) {
            boolean intersects = table.pageStart() <= clause.pageEnd()
                && table.pageEnd() >= clause.pageStart();
            double relevance = intersects ? requiredWeight(weights, "structural")
                + requiredWeight(weights, "pageProximity") : 0;
            if (relevance >= minimum) {
                candidates.add(new ContextItem("TABLE", table.id(),
                    table.markdownContent(), relevance,
                    Map.of("pageStart", table.pageStart(), "pageEnd", table.pageEnd())));
            }
        }

        List<ContextItem> selected = candidates.stream()
            .sorted((left, right) -> Double.compare(right.relevance(), left.relevance()))
            .limit(limit)
            .toList();
        Map<String, Object> features = new HashMap<>();
        features.put("contextItemCount", selected.size());
        features.put("hasTableContext",
            selected.stream().anyMatch(item -> "TABLE".equals(item.sourceType())));
        features.put("contextCharacterCount",
            selected.stream().mapToInt(item -> item.text() == null ? 0 : item.text().length()).sum());
        return new ClauseAnalysisContext(clause, selected, Map.copyOf(features));
    }

    private double requiredWeight(Map<String, Double> weights, String code) {
        Double value = weights.get(code);
        if (value == null) {
            throw new IllegalStateException("Context relevance weight is missing: " + code);
        }
        return value;
    }

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) {
            return result;
        }
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}
