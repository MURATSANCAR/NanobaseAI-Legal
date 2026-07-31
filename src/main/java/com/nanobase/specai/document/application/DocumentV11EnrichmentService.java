package com.nanobase.specai.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.capability.DefaultDocumentCapabilityProfiler;
import com.nanobase.specai.document.capability.DefaultDocumentProcessingRouter;
import com.nanobase.specai.document.capability.DocumentCapabilityProfile;
import com.nanobase.specai.document.capability.DocumentProcessingPlan;
import com.nanobase.specai.document.capability.DocumentProcessingPolicyVersion;
import com.nanobase.specai.document.capability.SpecIntelligenceV11Flags;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedPage;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedTable;
import com.nanobase.specai.document.ocr.DefaultNumericOcrIntegrityValidator;
import com.nanobase.specai.document.ocr.NumericOcrContext;
import com.nanobase.specai.document.ocr.NumericOcrValidationResult;
import com.nanobase.specai.document.table.CanonicalTable;
import com.nanobase.specai.document.table.CanonicalTableCell;
import com.nanobase.specai.document.table.CanonicalTableMapper;
import com.nanobase.specai.document.table.HeaderContextTableRequirementExtractionStrategy;
import com.nanobase.specai.document.table.TableExtractionProfile;
import com.nanobase.specai.document.table.TableRequirementExtractionResult;
import com.nanobase.specai.operations.application.FeatureFlagService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Feature-flagged v1.1 enrichment after core extraction persistence.
 * Does not alter v1.0 clause/requirement persistence paths when flags are off.
 */
@Service
public class DocumentV11EnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentV11EnrichmentService.class);
    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
        "(?iu)(?:\\d+[.,]?\\d*|IP\\s?\\d{2}|\\d+\\s?%|TS\\s?[A-Z0-9./-]+)");

    private final FeatureFlagService flags;
    private final DefaultDocumentCapabilityProfiler profiler;
    private final DefaultDocumentProcessingRouter router;
    private final DefaultNumericOcrIntegrityValidator numericValidator;
    private final HeaderContextTableRequirementExtractionStrategy tableRequirements;
    private final DocumentVersionRepository versions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DocumentV11EnrichmentService(
        FeatureFlagService flags,
        DefaultDocumentCapabilityProfiler profiler,
        DefaultDocumentProcessingRouter router,
        DefaultNumericOcrIntegrityValidator numericValidator,
        HeaderContextTableRequirementExtractionStrategy tableRequirements,
        DocumentVersionRepository versions,
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.flags = flags;
        this.profiler = profiler;
        this.router = router;
        this.numericValidator = numericValidator;
        this.tableRequirements = tableRequirements;
        this.versions = versions;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void enrich(
        UUID organizationId,
        UUID projectId,
        UUID documentVersionId,
        DocumentExtractionResult result,
        boolean ocrRequired
    ) {
        DocumentVersion version = versions.findByIdAndOrganizationId(documentVersionId, organizationId)
            .orElse(null);
        if (version == null || result == null) {
            return;
        }
        DocumentCapabilityProfile profile = null;
        if (flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.DOCUMENT_CAPABILITY_PROFILE)) {
            profile = profileAndPersist(organizationId, version, result, ocrRequired);
            DocumentProcessingPlan plan = router.resolve(
                profile,
                new DocumentProcessingPolicyVersion("DEFAULT_V11", "1.1.0", Map.of()));
            log.info(
                "event=document_profile_total organizationId={} versionId={} format={} mode={} "
                    + "parser={} ocr={}",
                organizationId, documentVersionId, profile.formatConceptCode(),
                profile.contentModeConceptCode(), plan.parserProviderCode(),
                plan.ocrProviderCode());
        }
        if (flags.enabled(organizationId, projectId, SpecIntelligenceV11Flags.OCR_QUALITY_GATES)
            || flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.OCR_NUMERIC_INTEGRITY)) {
            assessOcr(organizationId, projectId, documentVersionId, result, ocrRequired);
        }
        if (flags.enabled(organizationId, projectId, SpecIntelligenceV11Flags.CANONICAL_TABLE_CELLS)
            || flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.TABLE_REQUIREMENT_EXTRACTION)) {
            persistCanonicalTables(organizationId, projectId, documentVersionId, result);
        }
        if (profile != null && "DOCX".equals(profile.formatConceptCode())
            && flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.DOCX_STRUCTURE_PIPELINE)) {
            seedDocxBlocksFromClauses(organizationId, documentVersionId, result);
        }
    }

    private DocumentCapabilityProfile profileAndPersist(
        UUID organizationId,
        DocumentVersion version,
        DocumentExtractionResult result,
        boolean ocrRequired
    ) {
        int textChars = result.pages().stream()
            .mapToInt(page -> length(page.normalizedText()) + length(page.rawText()))
            .sum();
        double avgQuality = result.pages().isEmpty()
            ? result.textQualityScore()
            : result.pages().stream().mapToDouble(ExtractedPage::textQualityScore).average()
                .orElse(result.textQualityScore());
        boolean scanLikely = ocrRequired || avgQuality < 0.35d
            || (result.pageCount() > 0 && textChars < result.pageCount() * 40);
        DocumentCapabilityProfile profile = profiler.profile(
            organizationId,
            version.id(),
            version.mimeType(),
            extensionOf(version.originalFileName()),
            result.pageCount(),
            avgQuality,
            scanLikely,
            result.tables().size(),
            imageHints(result),
            textChars,
            Map.of("language", result.language() == null ? "" : result.language())
        );
        jdbc.update("delete from document_capability_profile where document_version_id = ?",
            version.id());
        jdbc.update("""
            insert into document_capability_profile (
                id, organization_id, document_version_id, format_concept_code,
                content_mode_concept_code, layout_complexity_concept_code, ocr_need_concept_code,
                table_density, image_density, text_density, heading_confidence,
                language_profile_json, page_count, estimated_token_count,
                recommended_parser_profile_code, recommended_ocr_profile_code,
                recommended_segmentation_profile_code, recommended_extraction_profile_code,
                created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            """,
            profile.id(), organizationId, version.id(),
            profile.formatConceptCode(), profile.contentModeConceptCode(),
            profile.layoutComplexityConceptCode(), profile.ocrNeedConceptCode(),
            profile.tableDensity(), profile.imageDensity(), profile.textDensity(),
            profile.headingConfidence(), json(profile.languageProfile()),
            profile.pageCount(), profile.estimatedTokenCount(),
            profile.recommendedParserProfileCode(), profile.recommendedOcrProfileCode(),
            profile.recommendedSegmentationProfileCode(),
            profile.recommendedExtractionProfileCode(),
            java.sql.Timestamp.from(profile.createdAt()));
        return profile;
    }

    private void assessOcr(
        UUID organizationId,
        UUID projectId,
        UUID documentVersionId,
        DocumentExtractionResult result,
        boolean ocrRequired
    ) {
        if (!ocrRequired && result.textQualityScore() >= 0.5d) {
            return;
        }
        boolean numericEnabled = flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.OCR_NUMERIC_INTEGRITY);
        jdbc.update("delete from ocr_quality_assessment where document_version_id = ?",
            documentVersionId);
        for (ExtractedPage page : result.pages()) {
            List<String> issues = new ArrayList<>();
            String status = page.textQualityScore() >= 0.75d ? "ACCEPT"
                : page.textQualityScore() >= 0.45d ? "REPROCESS"
                : page.textQualityScore() >= 0.20d ? "ALTERNATIVE_OCR"
                : page.textQualityScore() > 0d ? "MANUAL_REVIEW" : "UNUSABLE";
            double numericConfidence = 1.0d;
            if (numericEnabled) {
                Matcher matcher = NUMERIC_TOKEN.matcher(
                    page.normalizedText() == null ? "" : page.normalizedText());
                int checked = 0;
                int ambiguous = 0;
                while (matcher.find()) {
                    checked++;
                    NumericOcrValidationResult validation = numericValidator.validate(
                        new NumericOcrContext(matcher.group(), null, null));
                    if (validation.ambiguous()) {
                        ambiguous++;
                        issues.add("OCR_NUMERIC_AMBIGUITY:" + matcher.group());
                    }
                }
                if (checked > 0) {
                    numericConfidence = 1.0d - ((double) ambiguous / (double) checked);
                    if (ambiguous > 0 && "ACCEPT".equals(status)) {
                        status = "REPROCESS";
                    }
                }
            }
            if (page.textQualityScore() < 0.45d) {
                issues.add("OCR_LOW_QUALITY");
            }
            jdbc.update("""
                insert into ocr_quality_assessment (
                    id, organization_id, document_version_id, page_id, block_id,
                    character_confidence, word_confidence, layout_confidence,
                    language_confidence, numeric_confidence, quality_status_concept_code,
                    issues_json, created_at
                ) values (?, ?, ?, null, null, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
                UUID.randomUUID(), organizationId, documentVersionId,
                BigDecimal.valueOf(page.textQualityScore()),
                BigDecimal.valueOf(page.textQualityScore()),
                BigDecimal.valueOf(Math.min(1.0d, page.textQualityScore() + 0.05d)),
                BigDecimal.valueOf(0.7d),
                BigDecimal.valueOf(numericConfidence),
                status,
                json(issues));
        }
    }

    private void persistCanonicalTables(
        UUID organizationId,
        UUID projectId,
        UUID documentVersionId,
        DocumentExtractionResult result
    ) {
        List<Map<String, Object>> persisted = jdbc.queryForList("""
            select id, page_start, page_end, caption, structured_content_json, markdown_content
              from document_table
             where document_version_id = ? and organization_id = ?
             order by page_start, created_at
            """, documentVersionId, organizationId);
        if (persisted.isEmpty()) {
            return;
        }
        boolean cellsEnabled = flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.CANONICAL_TABLE_CELLS);
        boolean reqEnabled = flags.enabled(organizationId, projectId,
            SpecIntelligenceV11Flags.TABLE_REQUIREMENT_EXTRACTION);
        int index = 0;
        for (Map<String, Object> row : persisted) {
            UUID tableId = (UUID) row.get("id");
            ExtractedTable source = index < result.tables().size()
                ? result.tables().get(index) : null;
            Map<String, Object> structured = source == null
                ? parseMap(String.valueOf(row.get("structured_content_json")))
                : source.structuredContent();
            String markdown = source == null
                ? String.valueOf(row.getOrDefault("markdown_content", ""))
                : source.markdownContent();
            CanonicalTable canonical = CanonicalTableMapper.fromStructured(
                result.provider(),
                index,
                ((Number) row.get("page_start")).intValue(),
                ((Number) row.get("page_end")).intValue(),
                row.get("caption") == null ? null : String.valueOf(row.get("caption")),
                structured,
                markdown
            );
            jdbc.update("""
                update document_table
                   set table_index = ?,
                       title = coalesce(title, ?),
                       header_rows_json = ?::jsonb,
                       source_provider = ?,
                       confidence = ?
                 where id = ? and organization_id = ?
                """,
                index,
                canonical.title(),
                json(canonical.headerRows()),
                canonical.sourceProvider(),
                BigDecimal.valueOf(canonical.confidence()),
                tableId,
                organizationId);
            if (cellsEnabled) {
                jdbc.update("delete from document_table_cell where table_id = ?", tableId);
                for (CanonicalTableCell cell : canonical.cells()) {
                    jdbc.update("""
                        insert into document_table_cell (
                            id, organization_id, table_id, row_index, column_index,
                            row_span, column_span, text_content, normalized_text,
                            header_context_json, bounding_box_json, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now())
                        """,
                        UUID.randomUUID(), organizationId, tableId,
                        cell.rowIndex(), cell.columnIndex(), cell.rowSpan(), cell.columnSpan(),
                        nullToEmpty(cell.textContent()), nullToEmpty(cell.normalizedText()),
                        json(cell.headerContext()), json(cell.boundingBox()));
                }
            }
            if (reqEnabled) {
                TableRequirementExtractionResult extraction = tableRequirements.extract(
                    canonical, new TableExtractionProfile("HEADER_CONTEXT", true, 500));
                log.info(
                    "event=table_requirement_total organizationId={} tableId={} candidates={} "
                        + "warnings={}",
                    organizationId, tableId, extraction.candidates().size(),
                    extraction.warnings().size());
            }
            index++;
        }
    }

    private void seedDocxBlocksFromClauses(
        UUID organizationId,
        UUID documentVersionId,
        DocumentExtractionResult result
    ) {
        jdbc.update("delete from docx_structure_block where document_version_id = ?",
            documentVersionId);
        int order = 0;
        for (var clause : result.clauses()) {
            String type = clause.clauseType() == null ? "PARAGRAPH" : clause.clauseType();
            boolean heading = type.toUpperCase(Locale.ROOT).contains("HEAD")
                || (clause.clauseNumber() != null && !clause.clauseNumber().isBlank());
            jdbc.update("""
                insert into docx_structure_block (
                    id, organization_id, document_version_id, block_type_concept_code,
                    style_name, outline_level, numbering_id, list_level, text_content,
                    table_reference, parent_block_id, order_index, source_xml_path, created_at
                ) values (?, ?, ?, ?, ?, ?, null, null, ?, null, null, ?, ?, now())
                """,
                UUID.randomUUID(), organizationId, documentVersionId,
                heading ? "HEADING" : "PARAGRAPH",
                heading ? "semantic-heading" : "body",
                heading ? 1 : null,
                nullToEmpty(clause.normalizedText() == null || clause.normalizedText().isBlank()
                    ? clause.rawText() : clause.normalizedText()),
                order++,
                "clause:" + (clause.sourceId() == null ? order : clause.sourceId()));
        }
    }

    private static int imageHints(DocumentExtractionResult result) {
        Object meta = result.metadata().get("imageCount");
        if (meta instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Map.of();
    }
}
