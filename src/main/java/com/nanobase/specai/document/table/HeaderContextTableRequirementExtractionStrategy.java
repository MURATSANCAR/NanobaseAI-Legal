package com.nanobase.specai.document.table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds requirement statements that preserve header context
 * (parameter + value + description) instead of isolated cell values.
 */
@Component
public class HeaderContextTableRequirementExtractionStrategy
    implements TableRequirementExtractionStrategy {

    @Override
    public TableRequirementExtractionResult extract(
        CanonicalTable table,
        TableExtractionProfile profile
    ) {
        List<TableRequirementCandidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (table == null || table.cells() == null || table.cells().isEmpty()) {
            return new TableRequirementExtractionResult(List.of(), List.of("EMPTY_TABLE"));
        }
        int maxRows = profile == null ? 500 : Math.max(1, profile.maxRows());
        boolean requireHeader = profile == null || profile.requireHeaderContext();
        for (CanonicalTableCell cell : table.cells()) {
            if (cell.rowIndex() >= maxRows) {
                continue;
            }
            String text = cell.normalizedText() == null || cell.normalizedText().isBlank()
                ? cell.textContent() : cell.normalizedText();
            if (text == null || text.isBlank() || text.length() < 2) {
                continue;
            }
            Map<String, String> headers = cell.headerContext() == null
                ? Map.of() : cell.headerContext();
            if (requireHeader && headers.isEmpty()
                && (table.headerRows() == null || table.headerRows().isEmpty())) {
                warnings.add("TABLE_HEADER_CONTEXT_MISSING:r" + cell.rowIndex()
                    + "c" + cell.columnIndex());
                continue;
            }
            String statement = compose(headers, text, cell.columnIndex(), table.headerRows());
            if (statement.toLowerCase(Locale.ROOT).equals(text.toLowerCase(Locale.ROOT))
                && text.length() < 12 && text.matches("(?iu)^[a-z0-9./%-]+$")) {
                // Isolated short token without context is not a requirement statement.
                warnings.add("ISOLATED_CELL_SKIPPED:" + text);
                continue;
            }
            candidates.add(new TableRequirementCandidate(
                statement, headers, cell.rowIndex(), cell.columnIndex(), text));
        }
        return new TableRequirementExtractionResult(
            List.copyOf(candidates), List.copyOf(warnings));
    }

    private static String compose(
        Map<String, String> headers,
        String cellText,
        int columnIndex,
        List<List<String>> headerRows
    ) {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>(headers);
        if (ordered.isEmpty() && headerRows != null && !headerRows.isEmpty()) {
            for (List<String> row : headerRows) {
                if (columnIndex >= 0 && columnIndex < row.size()) {
                    String header = row.get(columnIndex);
                    if (header != null && !header.isBlank()) {
                        ordered.put("column", header.trim());
                        break;
                    }
                }
            }
        }
        if (ordered.isEmpty()) {
            return cellText.trim();
        }
        StringBuilder sb = new StringBuilder();
        ordered.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(key).append(": ").append(value);
        });
        sb.append(" => ").append(cellText.trim());
        return sb.toString();
    }
}
