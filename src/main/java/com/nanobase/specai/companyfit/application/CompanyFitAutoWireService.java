package com.nanobase.specai.companyfit.application;

import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentPage;
import com.nanobase.specai.document.domain.DocumentPageRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Best-effort hooks: company evidence READY → ingest; requirement extraction complete → evaluate.
 */
@Service
public class CompanyFitAutoWireService {
    private static final Logger log = LoggerFactory.getLogger(CompanyFitAutoWireService.class);

    private static final Set<DocumentType> EVIDENCE_TYPES = EnumSet.of(
        DocumentType.CERTIFICATE,
        DocumentType.PRODUCT_CATALOG,
        DocumentType.OTHER,
        DocumentType.FINANCIAL_FORM
    );

    private static final Set<DocumentType> TENDER_TYPES = EnumSet.of(
        DocumentType.TECHNICAL_SPECIFICATION,
        DocumentType.ADMINISTRATIVE_SPECIFICATION,
        DocumentType.DRAFT_CONTRACT,
        DocumentType.ADDENDUM,
        DocumentType.AMENDMENT,
        DocumentType.ANNEX
    );

    private final CompanyFitService companyFitService;
    private final DocumentRepository documents;
    private final DocumentPageRepository pages;

    public CompanyFitAutoWireService(
        CompanyFitService companyFitService,
        DocumentRepository documents,
        DocumentPageRepository pages) {
        this.companyFitService = companyFitService;
        this.documents = documents;
        this.pages = pages;
    }

    public void maybeIngestAfterParse(UUID organizationId, UUID documentId, UUID documentVersionId) {
        try {
            Document document = documents.findByIdAndOrganizationId(documentId, organizationId)
                .orElse(null);
            if (document == null || !EVIDENCE_TYPES.contains(document.documentType())) {
                return;
            }
            String text = loadText(organizationId, documentVersionId);
            if (text.isBlank()) {
                log.info("company-fit ingest skipped (empty text) documentId={}", documentId);
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", documentId.toString());
            payload.put("docType", document.documentType().name());
            payload.put("title", document.logicalName());
            payload.put("text", text);
            Map<String, Object> result = companyFitService.ingest(organizationId, List.of(payload));
            log.info("company-fit ingest ok documentId={} capabilityCount={}",
                documentId, result.get("capabilityCount"));
        } catch (Exception ex) {
            log.warn("company-fit ingest failed documentId={}: {}", documentId, ex.toString());
        }
    }

    public void maybeEvaluateAfterRequirements(UUID organizationId, UUID documentId) {
        try {
            Document document = documents.findByIdAndOrganizationId(documentId, organizationId)
                .orElse(null);
            if (document == null || !TENDER_TYPES.contains(document.documentType())) {
                return;
            }
            Map<String, Object> report = companyFitService.evaluate(
                organizationId, documentId, List.of(), List.of());
            log.info("company-fit evaluate ok documentId={} overall={} must={}/{}",
                documentId, report.get("overall"), report.get("mustMet"), report.get("mustTotal"));
        } catch (Exception ex) {
            log.warn("company-fit evaluate failed documentId={}: {}", documentId, ex.toString());
        }
    }

    private String loadText(UUID organizationId, UUID documentVersionId) {
        List<String> chunks = new ArrayList<>();
        int page = 0;
        while (page < 40) {
            var slice = pages.findAllByDocumentVersionIdAndOrganizationId(
                documentVersionId, organizationId, PageRequest.of(page, 50));
            for (DocumentPage item : slice.getContent()) {
                String normalized = item.normalizedText();
                String raw = item.rawText();
                String text = normalized != null && !normalized.isBlank() ? normalized : raw;
                if (text != null && !text.isBlank()) {
                    chunks.add(text.trim());
                }
            }
            if (!slice.hasNext()) {
                break;
            }
            page += 1;
        }
        return String.join("\n\n", chunks);
    }
}
