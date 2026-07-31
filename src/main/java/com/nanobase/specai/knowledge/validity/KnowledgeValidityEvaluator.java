package com.nanobase.specai.knowledge.validity;

public interface KnowledgeValidityEvaluator {
    KnowledgeValidityStatus evaluate(KnowledgeValidityInput input);
}
