package com.nanobase.specai.knowledge.application;

import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityCandidate;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityResolutionContext;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityResolutionResult;
import java.util.List;

public interface EntityResolutionService {
    EntityResolutionResult resolve(EntityResolutionContext context,
                                   List<EntityCandidate> candidates);
}
