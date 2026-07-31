package com.nanobase.specai.document.table;

import java.util.List;
import java.util.Map;

public record CanonicalTableCell(
    int rowIndex,
    int columnIndex,
    int rowSpan,
    int columnSpan,
    String textContent,
    String normalizedText,
    Map<String, String> headerContext,
    List<Object> boundingBox
) {
}
