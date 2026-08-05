# Categorizer lexicons (scale to 10k+)

Department-agnostic term banks for `requirement_categorizer_v2`.

## Layout

```
categorizer_lexicons/
  base_v23.json          # seed families (SECURITY, TECHNICAL, …)
  <any>.json             # optional overlays — all *.json merged at load
```

## JSON shape

```json
{
  "version": "2.3",
  "categories": {
    "TECHNICAL": {
      "compute": ["sunucu", "cpu", "..."],
      "storage": ["nvme", "raid", "..."]
    }
  },
  "tech_objects": ["dimm", "sunucu"],
  "bounded": { "TECHNICAL": ["cpu", "gpu"] },
  "extra_patterns": { "SCHEDULE": ["\\\\d+\\\\s*gün"] }
}
```

- **categories.<NAME>.\<family\>**: plain terms (TR/EN). Families are documentation only; matching is flat per category.
- **tech_objects**: nouns for `en az N` + object → TECHNICAL.
- **bounded**: forced `\\b…\\b` short tokens.
- **extra_patterns**: raw regex (lookbehinds, multi-token structures).

## How to grow to thousands

1. Add terms under the right **category + family** (not a department name).
2. Or drop another `*.json` beside `base_v23.json` (merged automatically).
3. Keep collisions intentional: higher priority wins (SECURITY > … > TECHNICAL > …).
4. Prefer phrases over 2–3 letter tokens. Single tokens with **len ≤ 3** are auto-bounded (`\b…\b`); still list critical acronyms under `bounded` explicitly.
5. Run:

```bash
python requirement_categorizer_v2.py
python -c "from categorizer_lexicon import lexicon_stats; print(lexicon_stats())"
pytest test_categorizer_title_normalize.py -q
```

6. Deploy: rebuild/recreate `document-intelligence` (JSON is COPY’d into the image).

## Do not

- Add an `IT_DEPT` / `LEGAL_DEPT` category — use semantic categories only.
- Put ISO 27001 / KVKK / gizlilik into COMPLIANCE (SECURITY owns them).
- Put bare `güvenlik` without the `(?<!iş\\s)` guard (breaks `iş güvenliği` → PERSONNEL).
