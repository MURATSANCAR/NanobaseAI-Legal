# OCR Numeric Integrity

`NumericOcrIntegrityValidator` flags ambiguous OCR numerics (0/O, 1/I/l, 5/S, separator confusion).

- Does **not** auto-correct values
- Emits issue codes + confidence penalty
- Source region hint retained for review

Error codes: `OCR_NUMERIC_AMBIGUITY`, `LOW_CHAR_CONFIDENCE_NUMERIC`.
