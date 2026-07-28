package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingInput;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingResult;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.analysis.domain.AnalysisProfileRepository;
import com.nanobase.specai.analysis.domain.Requirement;
import com.nanobase.specai.analysis.domain.RequirementRepository;
import com.nanobase.specai.analysis.domain.RequirementRevision;
import com.nanobase.specai.analysis.domain.RequirementRevisionRepository;
import com.nanobase.specai.analysis.infrastructure.AnalysisPersistenceStore;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementService {
    private final RequirementRepository requirements;
    private final RequirementRevisionRepository revisions;
    private final AnalysisProfileRepository profiles;
    private final ClauseRepository clauses;
    private final AnalysisPersistenceStore store;
    private final GroundingValidator groundingValidator;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    public RequirementService(RequirementRepository requirements,
                              RequirementRevisionRepository revisions,
                              AnalysisProfileRepository profiles,
                              ClauseRepository clauses,
                              AnalysisPersistenceStore store,
                              GroundingValidator groundingValidator,
                              ProjectAccessService access,
                              CurrentTenant currentTenant,
                              OutboxService outbox,
                              AuditService audit,
                              ObjectMapper mapper) {
        this.requirements = requirements;
        this.revisions = revisions;
        this.profiles = profiles;
        this.clauses = clauses;
        this.store = store;
        this.groundingValidator = groundingValidator;
        this.access = access;
        this.currentTenant = currentTenant;
        this.outbox = outbox;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<Requirement> list(UUID projectId, Pageable pageable) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return requirements.findAllByProjectIdAndOrganizationId(
            projectId, principal.tenantId(), pageable);
    }

    @Transactional(readOnly = true)
    public Requirement get(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        Requirement requirement = scoped(id, principal);
        access.requireView(requirement.projectId(), principal);
        return requirement;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sourceFragments(UUID id) {
        Requirement requirement = get(id);
        return store.sourceFragments(requirement.organizationId(), requirement.id());
    }

    @Transactional(readOnly = true)
    public List<RequirementRevision> revisions(UUID id) {
        Requirement requirement = get(id);
        return revisions.findAllByRequirementIdAndOrganizationIdOrderByRevisionNumber(
            id, requirement.organizationId());
    }

    @Transactional
    public Requirement update(UUID id, EditCommand command) {
        TenantPrincipal principal = currentTenant.require();
        Requirement requirement = scoped(id, principal);
        access.requireView(requirement.projectId(), principal);
        ObjectNode original = snapshotNode(requirement);
        Clause clause = clause(requirement, principal);
        List<String> fragments = command.sourceFragments() == null
            ? store.sourceFragments(principal.tenantId(), id).stream()
                .map(item -> String.valueOf(item.get("fragmentText"))).toList()
            : List.copyOf(command.sourceFragments());
        ObjectNode candidate = candidate(command.requirementCode() == null
                ? requirement.requirementCode() : command.requirementCode(),
            command.requirementText(), command.attributes(), fragments);
        GroundingResult grounding = groundingValidator.validate(new GroundingInput(
            clause.rawText(), fragments, candidate, Map.of()));
        if ("UNGROUNDED".equals(grounding.status())) {
            throw new IllegalArgumentException(
                "Expert edit is ungrounded and cannot replace the requirement");
        }
        Instant now = clock.instant();
        requirement.edit(command.title(), command.requirementText(),
            normalize(command.requirementText()), command.primaryConceptId(),
            command.modality(), command.modalityConceptId(), command.testabilityStatus(),
            command.conditionText(), command.subjectText(), command.actionText(),
            command.objectText(), json(command.attributes()),
            command.reviewStatus() == null ? "PENDING_REVIEW" : command.reviewStatus(), now);
        ObjectNode explanation = readObject(requirement.explanationJson());
        explanation.set("latestExpertGrounding", mapper.valueToTree(grounding));
        requirement.reground(grounding.status(), grounding.coverage(), json(explanation), now);
        Requirement saved = requirements.save(requirement);
        revision(saved, "EXPERT_EDIT", null, principal.subject(), now);
        UUID feedbackId = store.feedback(principal.tenantId(), saved.projectId(),
            "REQUIREMENT", saved.id(),
            command.feedbackType() == null ? "EXPERT_EDIT" : command.feedbackType(),
            original, snapshotNode(saved), command.reason(), principal.subject(),
            saved.analysisProfileId(), saved.ontologyVersionId(), saved.policyVersionId(),
            saved.promptVersionId(), saved.modelRunId(), command.approvedForLearning(), now);
        audit.record("REQUIREMENT_UPDATED", "Requirement", saved.id(),
            original, snapshotNode(saved));
        publishFeedback(saved, feedbackId);
        return saved;
    }

    @Transactional
    public Requirement review(UUID id, ReviewCommand command) {
        TenantPrincipal principal = currentTenant.require();
        Requirement requirement = scoped(id, principal);
        access.requireView(requirement.projectId(), principal);
        ObjectNode original = snapshotNode(requirement);
        requirement.review(command.reviewStatus(), clock.instant());
        Requirement saved = requirements.save(requirement);
        revision(saved, "EXPERT_EDIT", null, principal.subject(), clock.instant());
        UUID feedbackId = store.feedback(principal.tenantId(), saved.projectId(),
            "REQUIREMENT", saved.id(), command.feedbackType(), original,
            snapshotNode(saved), command.reason(), principal.subject(),
            saved.analysisProfileId(), saved.ontologyVersionId(), saved.policyVersionId(),
            saved.promptVersionId(), saved.modelRunId(), command.approvedForLearning(),
            clock.instant());
        audit.record("REQUIREMENT_REVIEWED", "Requirement", saved.id(),
            original, snapshotNode(saved));
        publishFeedback(saved, feedbackId);
        return saved;
    }

    @Transactional
    public List<Requirement> split(UUID id, SplitCommand command) {
        TenantPrincipal principal = currentTenant.require();
        Requirement original = scoped(id, principal);
        access.requireView(original.projectId(), principal);
        ObjectNode originalSnapshot = snapshotNode(original);
        if (command.parts() == null || command.parts().size() < 2) {
            throw new IllegalArgumentException("Split requires at least two grounded parts");
        }
        Clause clause = clause(original, principal);
        AnalysisProfile profile = profile(original, principal);
        List<Requirement> created = new ArrayList<>();
        Instant now = clock.instant();
        for (SplitPart part : command.parts()) {
            ObjectNode candidate = candidate(part.requirementCode(), part.requirementText(),
                part.attributes(), part.sourceFragments());
            GroundingResult grounding = groundingValidator.validate(new GroundingInput(
                clause.rawText(), part.sourceFragments(), candidate, Map.of()));
            if ("UNGROUNDED".equals(grounding.status())) {
                throw new IllegalArgumentException("Every split part must be source grounded");
            }
            Requirement child = new Requirement(UUID.randomUUID(), original.organizationId(),
                original.projectId(), original.documentId(), original.documentVersionId(),
                original.extractionJobId(), original.sourceClauseId(), part.requirementCode(),
                part.title(), part.requirementText(), normalize(part.requirementText()),
                part.primaryConceptId(), part.modality(), part.modalityConceptId(),
                part.testabilityStatus(), part.conditionText(), part.subjectText(),
                part.actionText(), part.objectText(), json(part.attributes()), "EXPERT_SPLIT",
                "PENDING_REVIEW", grounding.status(), grounding.coverage(), profile,
                original.modelRunId(), original.combinedConfidence(),
                json(Map.of("sourceRequirementId", original.id(),
                    "grounding", grounding)), now);
            requirements.saveAndFlush(child);
            store.sourceFragments(child, clause, part.sourceFragments(),
                grounding.evidence(), now);
            revision(child, "SPLIT", original.id(), principal.subject(), now);
            created.add(child);
        }
        original.review("SUPERSEDED_BY_SPLIT", now);
        revision(original, "SPLIT", created.getFirst().id(), principal.subject(), now);
        UUID feedbackId = store.feedback(principal.tenantId(), original.projectId(),
            "REQUIREMENT", original.id(), "INCORRECT_MERGE", originalSnapshot,
            mapper.valueToTree(created.stream().map(this::snapshotNode).toList()),
            command.reason(), principal.subject(), original.analysisProfileId(),
            original.ontologyVersionId(), original.policyVersionId(),
            original.promptVersionId(), original.modelRunId(),
            command.approvedForLearning(), now);
        audit.record("REQUIREMENT_SPLIT", "Requirement", original.id(),
            originalSnapshot,
            mapper.valueToTree(created.stream().map(this::snapshotNode).toList()));
        publishFeedback(original, feedbackId);
        return List.copyOf(created);
    }

    @Transactional
    public Requirement merge(UUID id, MergeCommand command) {
        TenantPrincipal principal = currentTenant.require();
        List<UUID> ids = new ArrayList<>();
        ids.add(id);
        if (command.requirementIds() != null) {
            command.requirementIds().stream().filter(candidate -> !candidate.equals(id))
                .forEach(ids::add);
        }
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Merge requires at least two requirements");
        }
        List<Requirement> sources = ids.stream().map(candidate -> scoped(candidate, principal))
            .toList();
        Requirement base = sources.getFirst();
        access.requireView(base.projectId(), principal);
        List<ObjectNode> sourceSnapshots = sources.stream()
            .map(this::snapshotNode).toList();
        if (sources.stream().anyMatch(item -> !item.projectId().equals(base.projectId()))) {
            throw new IllegalArgumentException("Requirements from different projects cannot merge");
        }
        AnalysisProfile profile = profile(base, principal);
        List<String> fragments = sources.stream()
            .flatMap(item -> store.sourceFragments(principal.tenantId(), item.id()).stream())
            .map(item -> String.valueOf(item.get("fragmentText"))).distinct().toList();
        String combinedSource = sources.stream()
            .map(item -> clause(item, principal).rawText())
            .distinct().reduce("", (left, right) -> left + "\n" + right);
        ObjectNode candidate = candidate(command.requirementCode(), command.requirementText(),
            command.attributes(), fragments);
        GroundingResult grounding = groundingValidator.validate(new GroundingInput(
            combinedSource, fragments, candidate, Map.of()));
        if ("UNGROUNDED".equals(grounding.status())) {
            throw new IllegalArgumentException("Merged requirement must remain source grounded");
        }
        Instant now = clock.instant();
        Requirement merged = new Requirement(UUID.randomUUID(), base.organizationId(),
            base.projectId(), base.documentId(), base.documentVersionId(),
            base.extractionJobId(), base.sourceClauseId(), command.requirementCode(),
            command.title(), command.requirementText(), normalize(command.requirementText()),
            command.primaryConceptId(), command.modality(), command.modalityConceptId(),
            command.testabilityStatus(), command.conditionText(), command.subjectText(),
            command.actionText(), command.objectText(), json(command.attributes()),
            "EXPERT_MERGE", "PENDING_REVIEW", grounding.status(), grounding.coverage(),
            profile, base.modelRunId(),
            sources.stream().mapToDouble(Requirement::combinedConfidence).min().orElse(0),
            json(Map.of("sourceRequirementIds", ids, "grounding", grounding)), now);
        requirements.saveAndFlush(merged);
        for (Requirement source : sources) {
            Clause sourceClause = clause(source, principal);
            List<String> sourceFragments = store.sourceFragments(
                principal.tenantId(), source.id()).stream()
                .map(item -> String.valueOf(item.get("fragmentText"))).toList();
            store.sourceFragments(merged, sourceClause, sourceFragments,
                grounding.evidence(), now);
            source.review("MERGED", now);
            revision(source, "MERGE", merged.id(), principal.subject(), now);
        }
        revision(merged, "MERGE", base.id(), principal.subject(), now);
        UUID feedbackId = store.feedback(principal.tenantId(), base.projectId(),
            "REQUIREMENT", merged.id(), "INCORRECT_SPLIT",
            mapper.valueToTree(sourceSnapshots),
            snapshotNode(merged), command.reason(), principal.subject(),
            merged.analysisProfileId(), merged.ontologyVersionId(),
            merged.policyVersionId(), merged.promptVersionId(), merged.modelRunId(),
            command.approvedForLearning(), now);
        audit.record("REQUIREMENTS_MERGED", "Requirement", merged.id(),
            mapper.valueToTree(sourceSnapshots),
            snapshotNode(merged));
        publishFeedback(merged, feedbackId);
        return merged;
    }

    private Requirement scoped(UUID id, TenantPrincipal principal) {
        return requirements.findByIdAndOrganizationId(id, principal.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Requirement not found: " + id));
    }

    private Clause clause(Requirement requirement, TenantPrincipal principal) {
        return clauses.findByIdAndDocumentVersionIdAndOrganizationId(
            requirement.sourceClauseId(), requirement.documentVersionId(), principal.tenantId())
            .orElseThrow(() -> new IllegalStateException("Requirement source clause is missing"));
    }

    private AnalysisProfile profile(Requirement requirement, TenantPrincipal principal) {
        return profiles.findByIdAndOrganizationId(
            requirement.analysisProfileId(), principal.tenantId())
            .orElseThrow(() -> new IllegalStateException("Analysis profile is missing"));
    }

    private void revision(Requirement requirement, String sourceType, UUID referenceId,
                          String actor, Instant now) {
        int number = Math.toIntExact(revisions.countByRequirementIdAndOrganizationId(
            requirement.id(), requirement.organizationId()) + 1);
        revisions.save(new RequirementRevision(UUID.randomUUID(),
            requirement.organizationId(), requirement.id(), number,
            json(snapshotNode(requirement)), sourceType, referenceId, actor, now));
    }

    private void publishFeedback(Requirement requirement, UUID feedbackId) {
        outbox.publish(requirement.organizationId(), "ExpertFeedback", feedbackId,
            "ExpertFeedbackRecorded", RabbitConfiguration.EXPERT_FEEDBACK,
            Map.of("feedbackId", feedbackId, "requirementId", requirement.id(),
                "projectId", requirement.projectId()), null);
    }

    private ObjectNode candidate(String code, String text, JsonNode attributes,
                                 List<String> fragments) {
        ObjectNode candidate = mapper.createObjectNode();
        candidate.put("requirementCode", code);
        candidate.put("requirementText", text);
        candidate.set("attributes", attributes == null ? mapper.createObjectNode() : attributes);
        candidate.set("sourceFragments", mapper.valueToTree(fragments));
        return candidate;
    }

    private ObjectNode snapshotNode(Requirement requirement) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", requirement.id().toString());
        node.put("requirementCode", requirement.requirementCode());
        putNullable(node, "title", requirement.title());
        node.put("requirementText", requirement.requirementText());
        if (requirement.primaryConceptId() != null) {
            node.put("primaryConceptId", requirement.primaryConceptId().toString());
        }
        putNullable(node, "modality", requirement.modality());
        node.set("attributes", read(requirement.attributesJson()));
        node.put("reviewStatus", requirement.reviewStatus());
        node.put("groundingStatus", requirement.groundingStatus());
        node.put("groundingCoverage", requirement.groundingCoverage());
        node.put("combinedConfidence", requirement.combinedConfidence());
        return node;
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private ObjectNode readObject(String value) {
        JsonNode node = read(value);
        return node.isObject() ? (ObjectNode) node : mapper.createObjectNode();
    }

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored requirement JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Requirement JSON cannot be serialized", exception);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Requirement text is required");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record EditCommand(
        String requirementCode, String title, String requirementText,
        UUID primaryConceptId, String modality, UUID modalityConceptId,
        String testabilityStatus, String conditionText, String subjectText,
        String actionText, String objectText, JsonNode attributes,
        List<String> sourceFragments, String reviewStatus, String feedbackType,
        String reason, boolean approvedForLearning
    ) {
    }

    public record ReviewCommand(String reviewStatus, String feedbackType, String reason,
                                boolean approvedForLearning) {
    }

    public record SplitCommand(List<SplitPart> parts, String reason,
                               boolean approvedForLearning) {
    }

    public record SplitPart(
        String requirementCode, String title, String requirementText,
        UUID primaryConceptId, String modality, UUID modalityConceptId,
        String testabilityStatus, String conditionText, String subjectText,
        String actionText, String objectText, JsonNode attributes,
        List<String> sourceFragments
    ) {
    }

    public record MergeCommand(
        List<UUID> requirementIds, String requirementCode, String title,
        String requirementText, UUID primaryConceptId, String modality,
        UUID modalityConceptId, String testabilityStatus, String conditionText,
        String subjectText, String actionText, String objectText, JsonNode attributes,
        String reason, boolean approvedForLearning
    ) {
    }
}
