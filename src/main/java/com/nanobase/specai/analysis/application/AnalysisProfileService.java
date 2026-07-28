package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ProfileInputs;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.analysis.domain.AnalysisProfileRepository;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentTableRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import com.nanobase.specai.tender.domain.TenderProject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisProfileService {
    private final AnalysisProfileRepository profiles;
    private final AnalysisCatalogPort catalog;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final ClauseRepository clauses;
    private final DocumentTableRepository tables;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final ObjectMapper mapper;
    private final OutboxService outbox;
    private final Clock clock = Clock.systemUTC();

    public AnalysisProfileService(AnalysisProfileRepository profiles,
                                  AnalysisCatalogPort catalog,
                                  DocumentRepository documents,
                                  DocumentVersionRepository versions,
                                  ClauseRepository clauses,
                                  DocumentTableRepository tables,
                                  ProjectAccessService access,
                                  CurrentTenant currentTenant,
                                  ObjectMapper mapper,
                                  OutboxService outbox) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.documents = documents;
        this.versions = versions;
        this.clauses = clauses;
        this.tables = tables;
        this.access = access;
        this.currentTenant = currentTenant;
        this.mapper = mapper;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<AnalysisProfile> list() {
        return profiles.findAllByOrganizationIdOrderByCreatedAtDesc(
            currentTenant.require().tenantId());
    }

    @Transactional(readOnly = true)
    public AnalysisProfile get(UUID id) {
        return profiles.findByIdAndOrganizationId(id, currentTenant.require().tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Analysis profile not found: " + id));
    }

    @Transactional(readOnly = true)
    public ProfilePreview preview(UUID documentId) {
        ProfileMaterial material = material(documentId);
        return new ProfilePreview(material.snapshot(), material.contentHash());
    }

    @Transactional
    public AnalysisProfile createSnapshot(UUID documentId) {
        ProfileMaterial material = material(documentId);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        AnalysisProfile profile = new AnalysisProfile(id, material.organizationId(),
            material.project().id(), material.document().id(), material.version().id(),
            json(material.snapshot().path("sectorContext")),
            json(material.snapshot().path("documentContext")),
            json(material.snapshot().path("terminologySetIds")),
            material.inputs().ontologyVersionId(),
            material.inputs().extractionPolicyVersionId(),
            material.inputs().promptPackageVersionId(),
            material.inputs().outputSchemaVersionId(),
            material.inputs().modelRoutingPolicyVersionId(),
            material.inputs().confidencePolicyVersionId(),
            json(material.snapshot()), material.contentHash(), now);
        profiles.save(profile);
        outbox.publish(profile.organizationId(), "AnalysisProfile", profile.id(),
            "AnalysisProfileCreated", RabbitConfiguration.ANALYSIS_PROFILE_CREATED,
            java.util.Map.of("profileId", profile.id(), "documentId", documentId),
            null);
        return profile;
    }

    private ProfileMaterial material(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndOrganizationId(documentId, principal.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        TenderProject project = access.requireDocumentView(document.projectId(), principal);
        if (document.currentVersionId() == null) {
            throw new IllegalArgumentException("Document has no current version");
        }
        DocumentVersion version = versions.findByIdAndOrganizationId(
            document.currentVersionId(), principal.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document version not found"));
        String language = version.language() == null ? "und" : version.language();
        ProfileInputs inputs = catalog.resolveProfileInputs(principal.tenantId(),
            project.sector(), document.documentType().name(), language);
        int clauseCount = clauses
            .findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
                version.id(), principal.tenantId()).size();
        long tableCount = tables.findAllByDocumentVersionIdAndOrganizationId(
            version.id(), principal.tenantId(), Pageable.unpaged()).getTotalElements();
        double tableDensity = clauseCount == 0 ? 0 : (double) tableCount / clauseCount;
        double ocrQuality = version.ocrQualityScore() == null ? 1
            : version.ocrQualityScore().doubleValue() / 100d;
        JsonNode extractionPolicy = catalog.policy(principal.tenantId(),
            inputs.extractionPolicyVersionId()).configuration();
        String complexity = complexity(extractionPolicy
            .path("documentProfile").path("structureComplexityLevels"), clauseCount);

        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("organizationId", principal.tenantId().toString());
        snapshot.put("projectId", project.id().toString());
        snapshot.put("documentId", document.id().toString());
        snapshot.put("documentVersionId", version.id().toString());
        ArrayNode sectorContext = snapshot.putArray("sectorContext");
        addIfPresent(sectorContext, project.sector());
        addIfPresent(sectorContext, project.tenderType());
        addIfPresent(sectorContext, project.businessType());
        ObjectNode documentContext = snapshot.putObject("documentContext");
        documentContext.put("documentType", document.documentType().name());
        documentContext.put("language", language);
        documentContext.put("structureComplexity", complexity);
        documentContext.put("clauseCount", clauseCount);
        documentContext.put("tableDensity", tableDensity);
        documentContext.put("ocrQuality", ocrQuality);
        documentContext.put("parserStatus", version.processingStatus().name());
        snapshot.put("ontologyVersionId", inputs.ontologyVersionId().toString());
        ArrayNode terminology = snapshot.putArray("terminologySetIds");
        inputs.terminologyCatalogIds().forEach(id -> terminology.add(id.toString()));
        snapshot.put("policyVersionId", inputs.extractionPolicyVersionId().toString());
        snapshot.put("promptPackageVersionId", inputs.promptPackageVersionId().toString());
        snapshot.put("outputSchemaVersionId", inputs.outputSchemaVersionId().toString());
        snapshot.put("modelRoutingPolicyId",
            inputs.modelRoutingPolicyVersionId().toString());
        snapshot.put("confidencePolicyId", inputs.confidencePolicyVersionId().toString());
        String serialized = json(snapshot);
        return new ProfileMaterial(principal.tenantId(), project, document, version,
            inputs, snapshot, sha256(serialized));
    }

    private String complexity(JsonNode levels, int clauseCount) {
        if (!levels.isArray() || levels.isEmpty()) {
            throw new IllegalStateException(
                "Document structure complexity levels are not configured");
        }
        for (JsonNode level : levels) {
            if (level.path("code").isTextual()
                && level.path("minimumClauseCount").isIntegralNumber()
                && clauseCount >= level.path("minimumClauseCount").intValue()) {
                return level.path("code").textValue();
            }
        }
        throw new IllegalStateException("Document structure complexity policy has a gap");
    }

    private void addIfPresent(ArrayNode values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analysis profile cannot be serialized", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ProfilePreview(JsonNode snapshot, String contentHash) {
    }

    private record ProfileMaterial(UUID organizationId, TenderProject project,
                                   Document document, DocumentVersion version,
                                   ProfileInputs inputs, ObjectNode snapshot,
                                   String contentHash) {
    }
}
