package com.nanobase.specai.document.table;

import java.util.List;

public record TableRequirementExtractionResult(
    List<TableRequirementCandidate> candidates,
    List<String> warnings
) {
}
