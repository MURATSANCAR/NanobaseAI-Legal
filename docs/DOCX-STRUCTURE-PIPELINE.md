# DOCX Structure Pipeline

`docx_structure_block` persists heading/list/table/footnote structure with `style_name`, `outline_level`, `numbering_id`, `source_xml_path`.

Rules:

- Do not flatten DOCX to plain text only
- Do not hardcode `Heading 1` as sole heading signal
- Archive safety limits remain policy-driven (`ArchiveSafetyInspector`)

Flag: `DOCX_STRUCTURE_PIPELINE_ENABLED`. Live E2E-03 **PENDING**.
