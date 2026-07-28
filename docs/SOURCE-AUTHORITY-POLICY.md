# Source Authority Policy

Kaynak güven sırası Java koduna gömülü değildir. Aktif `SOURCE_AUTHORITY`
`policy_version.configuration_json`, doküman türü bazlı `sourceScores`, issuer
override ve default score taşıyabilir. Tenant'a özel `source_authority_profile`,
ontology source-type concept'i ve opsiyonel issuer entity üzerinden policy'yi
daraltır.

`PolicySourceAuthorityEvaluator` önceliği profile score, issuer override, source score
ve default score olarak uygular ve sonucu 0..1 aralığında sınırlar. Candidate
retriever aynı tenant için profile/policy join'i yapar; eşleşme yoksa retrieval
policy'nin güvenli fallback puanını kullanır. Bu puan tek başına karar değildir;
validity, ontology, lexical, relation ve historical sinyallerle rerank edilir.
