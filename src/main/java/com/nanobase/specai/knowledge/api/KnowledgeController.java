package com.nanobase.specai.knowledge.api;

import com.nanobase.specai.knowledge.api.KnowledgeContracts.AttributeRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.CapabilityRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.CreateEntityRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.EntityDetailResponse;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.MergeRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.RelationRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.SplitRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.UpdateEntityRequest;
import com.nanobase.specai.knowledge.application.KnowledgeGraphService;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.AttributeView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.CapabilityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.RelationView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final KnowledgeGraphService knowledge;

    public KnowledgeController(KnowledgeGraphService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/entities")
    @ResponseStatus(HttpStatus.CREATED)
    EntityView create(@Valid @RequestBody CreateEntityRequest request) {
        return knowledge.create(request);
    }

    @GetMapping("/entities")
    List<EntityView> list(
        @RequestParam(required = false) UUID entityTypeConceptId,
        @RequestParam(required = false) String query
    ) {
        return knowledge.list(entityTypeConceptId, query);
    }

    @GetMapping("/entities/{id}")
    EntityDetailResponse get(@PathVariable UUID id) {
        return knowledge.get(id);
    }

    @PutMapping("/entities/{id}")
    EntityView update(@PathVariable UUID id,
                      @Valid @RequestBody UpdateEntityRequest request) {
        return knowledge.update(id, request);
    }

    @PostMapping("/entities/{id}/merge")
    EntityDetailResponse merge(@PathVariable UUID id,
                               @Valid @RequestBody MergeRequest request) {
        return knowledge.merge(id, request);
    }

    @PostMapping("/entities/{id}/split")
    List<EntityDetailResponse> split(@PathVariable UUID id,
                                     @Valid @RequestBody SplitRequest request) {
        return knowledge.split(id, request);
    }

    @GetMapping("/entities/{id}/revisions")
    List<Map<String, Object>> revisions(@PathVariable UUID id) {
        return knowledge.revisions(id);
    }

    @PostMapping("/entities/{id}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    AttributeView attribute(@PathVariable UUID id,
                            @Valid @RequestBody AttributeRequest request) {
        return knowledge.addAttribute(id, request);
    }

    @PutMapping("/attributes/{attributeId}")
    AttributeView updateAttribute(@PathVariable UUID attributeId,
                                  @Valid @RequestBody AttributeRequest request) {
        return knowledge.updateAttribute(attributeId, request);
    }

    @DeleteMapping("/attributes/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAttribute(@PathVariable UUID attributeId) {
        knowledge.endAttribute(attributeId);
    }

    @PostMapping("/relations")
    @ResponseStatus(HttpStatus.CREATED)
    RelationView relation(@Valid @RequestBody RelationRequest request) {
        return knowledge.createRelation(request);
    }

    @PutMapping("/relations/{id}")
    RelationView updateRelation(@PathVariable UUID id,
                                @Valid @RequestBody RelationRequest request) {
        return knowledge.updateRelation(id, request);
    }

    @DeleteMapping("/relations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRelation(@PathVariable UUID id) {
        knowledge.endRelation(id);
    }

    @PostMapping("/entities/{id}/capabilities")
    @ResponseStatus(HttpStatus.CREATED)
    CapabilityView capability(@PathVariable UUID id,
                              @Valid @RequestBody CapabilityRequest request) {
        return knowledge.createCapability(id, request);
    }

    @GetMapping("/entities/{id}/capabilities")
    List<CapabilityView> capabilities(@PathVariable UUID id) {
        return knowledge.capabilities(id);
    }

    @PutMapping("/capabilities/{id}")
    CapabilityView updateCapability(@PathVariable UUID id,
                                    @Valid @RequestBody CapabilityRequest request) {
        return knowledge.updateCapability(id, request);
    }
}
