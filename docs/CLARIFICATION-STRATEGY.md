# Clarification Strategy

`clarification_strategy_version`, configuration, prompt package ve output schema
sürümlerini birlikte sabitler. AI girdisi yalnız seçilmiş ambiguity/conflict
source’ları, authority policy, terminology ve kurum bağlamıdır.

AI orkestratörü:

- request dışı source ID’yi reddeder,
- bilinmeyen ontology concept’ini reddeder,
- authority rule olmadan source tercihini reddeder,
- delivery status’un `CANDIDATE` dışında olmasını reddeder.

API persistence öncesi source ID’leri tenant kapsamlı persisted source listesine
karşı tekrar doğrular. Candidate insan onayı olmadan dış kanala gönderilmez.
