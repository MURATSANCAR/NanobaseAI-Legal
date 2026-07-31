package com.nanobase.specai.document.table;

import java.util.Map;

public record TableRequirementCandidate(
    String statement,
    Map<String, String> headerContext,
    int rowIndex,
    int columnIndex,
    String sourceCellText
) {
}
