package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public interface DocumentChangeMatcher {
    record ClauseSnapshot(UUID id, String number, String title, String normalizedText,
                          String contentHash, int sortOrder) {
    }

    record Match(UUID baseClauseId, UUID targetClauseId, String changeConceptCode,
                 double similarity, double confidence, JsonNode attributes) {
    }

    List<Match> match(List<ClauseSnapshot> base, List<ClauseSnapshot> target,
                      RiskModels.VersionedPolicy policy);
}
