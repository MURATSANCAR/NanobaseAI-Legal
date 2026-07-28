# Human Disagreement ve Adjudication

`review_disagreement` bağımsız Uzman A ve Uzman B kararlarını JSON snapshot olarak
saklar. Reviewer kimlikleri farklı olmalıdır.

Akış:

`Uzman A → Uzman B → IN_ADJUDICATION → bağımsız kıdemli uzman → RESOLVED`

Adjudicator iki reviewer’dan biri olamaz. Final decision ve resolved time birlikte
yazılır; kesin label yalnız bu noktadan sonra kullanılabilir. Disagreement türleri
dinamik katalogdan gelir.

API:

- `POST /api/v1/review-disagreements`
- `POST /api/v1/review-disagreements/{id}/adjudicate`
