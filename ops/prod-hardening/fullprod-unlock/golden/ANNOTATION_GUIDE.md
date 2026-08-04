# Golden annotation guide

Hedef: **8–10 farklı gerçek ihale PDF’i**, her birinde **4–8 kritik madde**.

## Seçim kuralları

- Aynı PDF’i 10 slot’a kopyalama (bootstrap clone geçersiz).
- Farklı kurum / tür karışımı tercih: teknik şartname, idari şartname, zeyilname, sözleşme taslağı.
- Sayfa ≤120 tercih (Markdown short-circuit yolu için).
- Taranmış PDF ayrı slot olabilir; short-circuit bekleme.

## Nasıl işaretlenir

1. PDF’i aç, içindekiler / madde başlıklarını oku.
2. Kritik maddeleri seç (ör. kapsam, teminat, süre, ceza, ISO/belge, teslimat).
3. `title`: belgedeki heading ile birebir (küçük fark OK; eval substring match).
4. `contentContains`: 2–4 ayırt edici kelime/ifade (normalize edilmiş eşleşme).
5. `pageStart` / `pageEnd`: yaklaşık aralık (±1 sayfa toleranslı overlap).
6. `forbiddenPatterns`: bozuk encoding (`\ufffd`) + sahte filler.

Şablon: `expected_template.json` → kopyala → `expected.json`.

## Actual üretimi

Canlı parse sonucu clauses’ı `actual.json` formatına koy:

```json
{
  "documents": [
    {
      "documentId": "…",
      "clauses": [
        {"title": "…", "rawText": "…", "pageStart": 1, "pageEnd": 2}
      ]
    }
  ]
}
```

## Eval

```bash
python ops/prod-hardening/evaluation/clause_quality_eval.py \
  --expected /data/fixtures/golden/expected.json \
  --actual /data/fixtures/golden/actual.json \
  --report /tmp/clause-quality-report.json \
  --min-pass-rate 0.80
```

Gate: `passRate >= 0.80` ve rapor `passed: true`.
