package com.nanobase.specai.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.DocumentPage;
import com.nanobase.specai.document.domain.DocumentPageRepository;
import com.nanobase.specai.document.domain.DocumentTable;
import com.nanobase.specai.document.domain.DocumentTableRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.ParserWarning;
import com.nanobase.specai.document.domain.ParserWarningRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentExtractionPersistenceService {
    private final DocumentVersionRepository versions;
    private final DocumentPageRepository pages;
    private final ClauseRepository clauses;
    private final DocumentTableRepository tables;
    private final ParserWarningRepository warnings;
    private final TenantDatabaseContext tenantContext;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics metrics;
    private final Clock clock = Clock.systemUTC();

    public DocumentExtractionPersistenceService(
        DocumentVersionRepository versions,
        DocumentPageRepository pages,
        ClauseRepository clauses,
        DocumentTableRepository tables,
        ParserWarningRepository warnings,
        TenantDatabaseContext tenantContext,
        ObjectMapper objectMapper,
        PlatformMetrics metrics) {
        this.versions = versions;
        this.pages = pages;
        this.clauses = clauses;
        this.tables = tables;
        this.warnings = warnings;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void persist(UUID organizationId, UUID processingJobId,
                        DocumentExtractionResult result, boolean ocrRequired) {
        tenantContext.apply(organizationId);
        DocumentVersion version = versions.findForUpdate(
            result.documentVersionId(), organizationId)
            .orElseThrow(() -> new InvalidDocumentException(
                "Document version was not found"));
        pages.deleteAllByDocumentVersionIdAndOrganizationId(
            version.id(), organizationId);
        clauses.deleteAllByDocumentVersionIdAndOrganizationId(
            version.id(), organizationId);
        tables.deleteAllByDocumentVersionIdAndOrganizationId(
            version.id(), organizationId);
        Instant now = clock.instant();
        pages.saveAll(result.pages().stream().map(page -> new DocumentPage(
            UUID.randomUUID(), organizationId, version.id(), page.pageNumber(),
            BigDecimal.valueOf(page.width()), BigDecimal.valueOf(page.height()),
            page.rotation(), nullToEmpty(page.rawText()), nullToEmpty(page.normalizedText()),
            BigDecimal.valueOf(page.textQualityScore()), page.thumbnailObjectKey(), now
        )).toList());
        Map<String, UUID> sourceIds = new HashMap<>();
        result.clauses().stream()
            .sorted(Comparator.comparingInt(clause -> clause.sortOrder()))
            .forEach(clause -> {
                UUID id = UUID.randomUUID();
                UUID parentId = null;
                if (clause.parentSourceId() != null) {
                    parentId = sourceIds.get(clause.parentSourceId());
                    if (parentId == null) {
                        throw new IllegalArgumentException(
                            "Clause parent must precede its child");
                    }
                }
                clauses.save(new Clause(id, organizationId, version.id(), parentId,
                    clause.clauseNumber(), clause.title(), nullToEmpty(clause.rawText()),
                    nullToEmpty(clause.normalizedText()), clause.clauseType(),
                    clause.pageStart(), clause.pageEnd(), json(clause.boundingBoxes()),
                    hashOrCalculate(clause.contentHash(), clause.normalizedText()),
                    clause.sortOrder(), now));
                if (clause.sourceId() != null) {
                    sourceIds.put(clause.sourceId(), id);
                }
            });
        tables.saveAll(result.tables().stream().map(table -> new DocumentTable(
            UUID.randomUUID(), organizationId, version.id(), table.pageStart(),
            table.pageEnd(), table.caption(), table.markdownContent(),
            json(table.structuredContent()), json(table.boundingBoxes()),
            hashOrCalculate(table.contentHash(), table.markdownContent()), now
        )).toList());
        warnings.saveAll(result.warnings().stream().map(warning -> new ParserWarning(
            UUID.randomUUID(), organizationId, version.id(), processingJobId,
            warning.warningCode(), safeSeverity(warning.severity()), safeMessage(warning.message()),
            warning.pageNumber(), json(warning.metadata()), now
        )).toList());
        version.extracted(result.pageCount(), result.language(),
            result.textQualityScore(), ocrRequired);
        metrics.pagesExtracted(result.pages().size());
        metrics.clausesExtracted(result.clauses().size());
        metrics.parserWarnings(result.warnings().size());
    }

    private String hashOrCalculate(String hash, String content) {
        if (hash != null && hash.matches("[a-fA-F0-9]{64}")) {
            return hash.toLowerCase(java.util.Locale.ROOT);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(nullToEmpty(content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeSeverity(String value) {
        return switch (value == null ? "" : value.toUpperCase(java.util.Locale.ROOT)) {
            case "ERROR" -> "ERROR";
            case "INFO" -> "INFO";
            default -> "WARNING";
        };
    }

    private String safeMessage(String value) {
        String resolved = value == null ? "Parser warning" : value;
        return resolved.length() <= 1000 ? resolved : resolved.substring(0, 1000);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                "Provider result cannot be serialized", exception);
        }
    }
}
