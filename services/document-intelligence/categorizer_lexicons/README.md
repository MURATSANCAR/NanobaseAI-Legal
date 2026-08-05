# Categorizer lexicons (scale to 10k+)

Department-agnostic term banks for `requirement_categorizer_v2`.

## Layout

```
categorizer_lexicons/
  base_v23.json          # curated seed families
  learned_overlay.json   # human-accepted organic terms (harvest → accept)
  candidates.jsonl       # pending queue (local; not merged as lexicon)
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

## Organic growth (harvest → accept)

Şartname okudukça liste otomatik şişmez; aday kuyruğu büyür, onaylanınca lexicon’a girer.

```bash
cd services/document-intelligence

# 1) Parse sonucu / requirements JSON'dan OTHER maddeleri hasat et
python lexicon_harvest.py harvest -i /path/to/parse_result.json

# 2) Bekleyen adaylar
python lexicon_harvest.py status

# 3) Onay (self_check kapısı — bozarsa rollback)
python lexicon_harvest.py accept --term "spine-leaf" --category TECHNICAL --family network_hw --tech-object

# 4) Red
python lexicon_harvest.py reject --term "foobar"

python requirement_categorizer_v2.py
pytest test_lexicon_harvest.py test_categorizer_title_normalize.py -q
```

`CATEGORIZER_LEXICON_DIR` ile alternatif klasör verilebilir (test / sandbox).

## How to grow to thousands

1. Prefer `lexicon_harvest.py` for real şartname terms; or edit JSON families by hand.
2. Or drop another `*.json` beside `base_v23.json` (merged automatically).
3. Keep collisions intentional: higher priority wins (SECURITY > … > TECHNICAL > …).
4. Prefer phrases over 2–3 letter tokens. Single tokens with **len ≤ 3** are auto-bounded (`\b…\b`).
5. Deploy: rebuild/recreate `document-intelligence` after `learned_overlay.json` changes.

## Do not

- Auto-merge candidates into live lexicon without accept.
- Add an `IT_DEPT` / `LEGAL_DEPT` category — use semantic categories only.
- Put ISO 27001 / KVKK / gizlilik into COMPLIANCE (SECURITY owns them).
- Put bare `güvenlik` without the `(?<!iş\\s)` guard (breaks `iş güvenliği` → PERSONNEL).
