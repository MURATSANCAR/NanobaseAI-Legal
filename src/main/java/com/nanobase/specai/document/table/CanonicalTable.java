package com.nanobase.specai.document.table;

import java.util.List;
import java.util.Map;

/**
 * Canonical table shared by PDF and DOCX extractors.
 */
public record CanonicalTable(
    String sourceProvider,
    int tableIndex,
    Integer pageStart,
    Integer pageEnd,
    String title,
    String caption,
    List<List<String>> headerRows,
    List<CanonicalTableCell> cells,
    double confidence,
    Map<String, Object> metadata
) {
}
