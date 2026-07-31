# OCR Quality Model

`ocr_quality_assessment` stores character/word/layout/language/numeric confidences and a quality status concept code with `issues_json`.

Downstream requirement confidence should consume numeric integrity penalties (`DefaultNumericOcrIntegrityValidator`) without inventing corrected digits.

Flag: `OCR_QUALITY_GATES_ENABLED` / `OCR_NUMERIC_INTEGRITY_ENABLED`.
