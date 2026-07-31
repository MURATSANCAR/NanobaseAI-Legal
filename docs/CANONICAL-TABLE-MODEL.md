# Canonical Table Model

Shared PDF/DOCX model:

- Existing `document_table` extended with `table_index`, `title`, `header_rows_json`, `source_provider`, `confidence`
- New `document_table_cell` with row/column/span, header context JSON, bounding box

Java: `CanonicalTable` / `CanonicalTableCell`.

Flag: `CANONICAL_TABLE_CELLS_ENABLED`.
