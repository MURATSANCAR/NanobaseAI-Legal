# Conflict ve Ambiguity UI

Çelişki ekranı üç paneldir: Kaynak A, Kaynak B ve analiz/uzman kararı.
Doküman/page/clause metni, comparison strategy, confidence, review durumu ve
authority sınırı görünür. Her iki kaynak PDF’e açılır.

Belirsizlik ekranı finding listesi + detay panelidir. Concept, confidence,
structured missing fields, kaynak clause, olası yorumlar ve clarification
candidate sınırı gösterilir.

Her iki ekran API’den yüklenir. Approve/reject işlemleri audit, revision
(conflict/risk) ve `expert_feedback` üretir. Clarification dışarı gönderilmez.
