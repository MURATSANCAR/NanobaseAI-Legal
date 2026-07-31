package com.nanobase.specai.document.table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps provider-specific table payloads into the shared canonical table model.
 * Supports Docling-like grids and markdown pipe tables without PDF/DOCX forks.
 */
public final class CanonicalTableMapper {
    private CanonicalTableMapper() {
    }

    @SuppressWarnings("unchecked")
    public static CanonicalTable fromStructured(
        String sourceProvider,
        int tableIndex,
        Integer pageStart,
        Integer pageEnd,
        String caption,
        Map<String, Object> structuredContent,
        String markdownContent
    ) {
        Map<String, Object> data = structuredContent == null ? Map.of() : structuredContent;
        List<CanonicalTableCell> cells = new ArrayList<>();
        List<List<String>> headerRows = new ArrayList<>();

        Object gridCells = firstPresent(data, "grid_cells", "gridCells", "table_cells", "cells");
        if (gridCells instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> cell = castMap(raw);
                int row = intAt(cell, 0, "row_index", "rowIndex", "start_row_offset", "startRowOffset");
                int col = intAt(cell, 0, "column_index", "columnIndex", "start_col_offset",
                    "startColOffset");
                int rowSpan = Math.max(1, intAt(cell, 1, "row_span", "rowSpan"));
                int colSpan = Math.max(1, intAt(cell, 1, "column_span", "columnSpan",
                    "col_span", "colSpan"));
                String text = stringAt(cell, "text", "text_content", "textContent", "value");
                boolean header = boolAt(cell, "column_header", "columnHeader", "row_header",
                    "rowHeader", "header");
                Map<String, String> headerContext = new LinkedHashMap<>();
                Object hc = cell.get("header_context");
                if (hc == null) {
                    hc = cell.get("headerContext");
                }
                if (hc instanceof Map<?, ?> map) {
                    map.forEach((k, v) -> headerContext.put(String.valueOf(k), String.valueOf(v)));
                }
                if (header) {
                    ensureHeaderRow(headerRows, row, col, text);
                }
                cells.add(new CanonicalTableCell(
                    row, col, rowSpan, colSpan, text, normalize(text),
                    Map.copyOf(headerContext), List.of()));
            }
        }

        if (cells.isEmpty() && markdownContent != null && !markdownContent.isBlank()) {
            parseMarkdown(markdownContent, cells, headerRows);
        }

        if (headerRows.isEmpty() && !cells.isEmpty()) {
            int minRow = cells.stream().mapToInt(CanonicalTableCell::rowIndex).min().orElse(0);
            List<CanonicalTableCell> first = cells.stream()
                .filter(c -> c.rowIndex() == minRow)
                .sorted((a, b) -> Integer.compare(a.columnIndex(), b.columnIndex()))
                .toList();
            if (!first.isEmpty()) {
                List<String> headers = first.stream().map(CanonicalTableCell::textContent).toList();
                headerRows.add(headers);
                cells = enrichHeaderContext(cells, headers, minRow);
            }
        } else if (!headerRows.isEmpty()) {
            cells = enrichHeaderContext(cells, headerRows.get(0),
                cells.stream().mapToInt(CanonicalTableCell::rowIndex).min().orElse(0));
        }

        return new CanonicalTable(
            sourceProvider == null ? "UNKNOWN" : sourceProvider,
            tableIndex,
            pageStart,
            pageEnd,
            caption,
            caption,
            List.copyOf(headerRows),
            List.copyOf(cells),
            cells.isEmpty() ? 0.2d : 0.75d,
            Map.of("sourceKeys", data.keySet().stream().map(String::valueOf).toList())
        );
    }

    private static List<CanonicalTableCell> enrichHeaderContext(
        List<CanonicalTableCell> cells,
        List<String> headers,
        int headerRow
    ) {
        List<CanonicalTableCell> out = new ArrayList<>(cells.size());
        for (CanonicalTableCell cell : cells) {
            if (cell.rowIndex() == headerRow || !cell.headerContext().isEmpty()) {
                out.add(cell);
                continue;
            }
            Map<String, String> ctx = new LinkedHashMap<>(cell.headerContext());
            if (cell.columnIndex() >= 0 && cell.columnIndex() < headers.size()) {
                String header = headers.get(cell.columnIndex());
                if (header != null && !header.isBlank()) {
                    ctx.putIfAbsent("column", header.trim());
                }
            }
            out.add(new CanonicalTableCell(
                cell.rowIndex(), cell.columnIndex(), cell.rowSpan(), cell.columnSpan(),
                cell.textContent(), cell.normalizedText(), Map.copyOf(ctx), cell.boundingBox()));
        }
        return out;
    }

    private static void parseMarkdown(
        String markdown,
        List<CanonicalTableCell> cells,
        List<List<String>> headerRows
    ) {
        String[] lines = markdown.split("\\R");
        int row = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") || trimmed.replace("|", "").isBlank()) {
                continue;
            }
            if (trimmed.matches("^\\|?[\\s:-]+\\|([\\s|:-]+)$")) {
                continue;
            }
            List<String> cols = new ArrayList<>();
            for (String part : trimmed.split("\\|", -1)) {
                String cell = part.trim();
                if (!cell.isEmpty() || !cols.isEmpty()) {
                    cols.add(cell);
                }
            }
            if (!cols.isEmpty() && cols.get(cols.size() - 1).isBlank()) {
                cols.remove(cols.size() - 1);
            }
            if (cols.isEmpty()) {
                continue;
            }
            if (headerRows.isEmpty()) {
                headerRows.add(List.copyOf(cols));
            }
            for (int col = 0; col < cols.size(); col++) {
                String text = cols.get(col);
                Map<String, String> ctx = new LinkedHashMap<>();
                if (!headerRows.isEmpty() && col < headerRows.get(0).size() && row > 0) {
                    ctx.put("column", headerRows.get(0).get(col));
                }
                cells.add(new CanonicalTableCell(
                    row, col, 1, 1, text, normalize(text), Map.copyOf(ctx), List.of()));
            }
            row++;
        }
    }

    private static void ensureHeaderRow(
        List<List<String>> headerRows, int row, int col, String text
    ) {
        while (headerRows.size() <= row) {
            headerRows.add(new ArrayList<>());
        }
        List<String> target = new ArrayList<>(headerRows.get(row));
        while (target.size() <= col) {
            target.add("");
        }
        target.set(col, text == null ? "" : text);
        headerRows.set(row, target);
    }

    private static Object firstPresent(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key) && data.get(key) != null) {
                return data.get(key);
            }
        }
        return null;
    }

    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private static int intAt(Map<String, Object> map, int fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return fallback;
    }

    private static boolean boolAt(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null && "true".equalsIgnoreCase(String.valueOf(value))) {
                return true;
            }
        }
        return false;
    }

    private static String stringAt(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
