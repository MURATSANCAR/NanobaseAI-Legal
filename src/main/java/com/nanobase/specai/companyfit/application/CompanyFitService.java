package com.nanobase.specai.companyfit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyCapability;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyFitReport;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CapabilityKind;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyFitService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CompanyCapabilityExtractor extractor;
    private final CompanyFitEngine engine;

    public CompanyFitService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                             CompanyCapabilityExtractor extractor, CompanyFitEngine engine) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.extractor = extractor;
        this.engine = engine;
    }

    @Transactional
    public Map<String, Object> ingest(UUID organizationId, List<Map<String, Object>> documents) {
        List<CompanyCapability> all = new ArrayList<>();
        Instant now = Instant.now();
        for (Map<String, Object> doc : documents) {
            UUID docId = parseUuid(doc.get("documentId"), UUID.randomUUID());
            String docType = str(doc.get("docType"), "OTHER");
            String title = str(doc.get("title"), null);
            String text = str(doc.get("text"), "");
            jdbc.update("""
                insert into company_document
                    (id, organization_id, doc_type, title, content_hash, source_document_id,
                     created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    title = excluded.title,
                    updated_at = excluded.updated_at
                """,
                docId, organizationId, docType, title,
                Integer.toHexString(text.hashCode()), docId,
                Timestamp.from(now), Timestamp.from(now));

            List<CompanyCapability> caps = extractor.extract(
                organizationId.toString(), docId.toString(), text);
            for (CompanyCapability cap : caps) {
                persistCapability(organizationId, docId, cap);
                all.add(cap);
            }
        }
        return Map.of(
            "organizationId", organizationId.toString(),
            "capabilityCount", all.size(),
            "capabilities", all.stream().map(CompanyCapability::toMap).toList()
        );
    }

    @Transactional
    public Map<String, Object> evaluate(UUID organizationId, UUID tenderDocumentId,
                                        List<Map<String, Object>> requirements,
                                        List<Map<String, Object>> capabilityPayload) {
        List<Map<String, Object>> reqs = requirements;
        if (reqs == null || reqs.isEmpty()) {
            reqs = loadRequirements(organizationId, tenderDocumentId);
        }
        List<CompanyCapability> caps;
        if (capabilityPayload != null && !capabilityPayload.isEmpty()) {
            caps = capabilityPayload.stream().map(this::fromPayload).toList();
        } else {
            caps = loadCapabilities(organizationId);
        }
        CompanyFitReport report = engine.buildReport(
            organizationId.toString(), tenderDocumentId.toString(), reqs, caps);
        UUID reportId = UUID.randomUUID();
        try {
            jdbc.update("""
                insert into company_fit_report
                    (id, organization_id, tender_document_id, overall, overall_score,
                     must_met, must_total, missing_critical, rows_json, policy_version, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """,
                reportId, organizationId, tenderDocumentId,
                report.overall().name(), report.overallScore(),
                report.mustMet(), report.mustTotal(),
                objectMapper.writeValueAsString(report.missingCritical()),
                objectMapper.writeValueAsString(report.rows().stream().map(r -> r.toMap()).toList()),
                report.policyVersion(), Timestamp.from(Instant.now()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize fit report", ex);
        }
        Map<String, Object> out = new LinkedHashMap<>(report.toMap());
        out.put("id", reportId.toString());
        return out;
    }

    public List<Map<String, Object>> listCapabilities(UUID organizationId) {
        return loadCapabilities(organizationId).stream().map(CompanyCapability::toMap).toList();
    }

    public List<Map<String, Object>> latestFit(UUID organizationId, UUID tenderDocumentId) {
        return jdbc.queryForList("""
            select id, overall, overall_score, must_met, must_total, missing_critical,
                   rows_json, policy_version, created_at
              from company_fit_report
             where organization_id = ? and tender_document_id = ?
             order by created_at desc
             limit 5
            """, organizationId, tenderDocumentId);
    }

    private void persistCapability(UUID organizationId, UUID companyDocId, CompanyCapability cap) {
        try {
            jdbc.update("""
                insert into company_capability
                    (id, organization_id, kind, canonical_key, label, attributes_json,
                     valid_to, validity_status, source_document_id, company_document_id,
                     evidence_snippet, confidence, created_at)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.fromString(cap.capabilityId()), organizationId, cap.kind().name(),
                cap.canonicalKey(), cap.label(),
                objectMapper.writeValueAsString(cap.attributes() == null ? Map.of() : cap.attributes()),
                parseDate(cap.validTo()), cap.validityStatus(),
                parseUuid(cap.sourceDocumentId(), null), companyDocId,
                cap.evidenceSnippet(), cap.confidence(), Timestamp.from(Instant.now()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize capability attributes", ex);
        }
    }

    private List<CompanyCapability> loadCapabilities(UUID organizationId) {
        return jdbc.query("""
            select id, kind, canonical_key, label, attributes_json, valid_from, valid_to,
                   validity_status, source_document_id, evidence_snippet, confidence
              from company_capability
             where organization_id = ?
             order by created_at desc
            """, (rs, row) -> {
            Map<String, Object> attrs;
            try {
                attrs = objectMapper.readValue(rs.getString("attributes_json"), Map.class);
            } catch (Exception ex) {
                attrs = Map.of();
            }
            Date validTo = rs.getDate("valid_to");
            Date validFrom = rs.getDate("valid_from");
            UUID source = (UUID) rs.getObject("source_document_id");
            return new CompanyCapability(
                rs.getObject("id").toString(),
                organizationId.toString(),
                CapabilityKind.valueOf(rs.getString("kind")),
                rs.getString("canonical_key"),
                rs.getString("label"),
                attrs,
                validFrom == null ? null : validFrom.toString(),
                validTo == null ? null : validTo.toString(),
                rs.getString("validity_status"),
                source == null ? null : source.toString(),
                rs.getString("evidence_snippet"),
                rs.getDouble("confidence")
            );
        }, organizationId);
    }

    private List<Map<String, Object>> loadRequirements(UUID organizationId, UUID documentId) {
        return jdbc.query("""
            select id, title, requirement_text, requirement_type, obligation_level,
                   attributes_json
              from requirement
             where organization_id = ? and document_id = ?
               and requirement_status = 'ACTIVE'
             order by created_at
            """, (rs, row) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requirementId", rs.getObject("id").toString());
            m.put("title", rs.getString("title"));
            m.put("text", rs.getString("requirement_text"));
            m.put("category", rs.getString("requirement_type"));
            String obl = rs.getString("obligation_level");
            m.put("obligationLevel", "MANDATORY".equalsIgnoreCase(obl) ? "MUST" : obl);
            m.put("priority", m.get("obligationLevel"));
            try {
                Map<?, ?> attrs = objectMapper.readValue(rs.getString("attributes_json"), Map.class);
                if (attrs != null) {
                    if (attrs.get("measurement") != null) {
                        m.put("measurement", attrs.get("measurement"));
                    }
                    if (attrs.get("acceptanceThreshold") != null) {
                        m.put("acceptanceThreshold", attrs.get("acceptanceThreshold"));
                    }
                    if (attrs.get("operator") != null) {
                        m.put("operator", attrs.get("operator"));
                    }
                }
            } catch (Exception ignored) {
                // keep text-only requirement
            }
            return m;
        }, organizationId, documentId);
    }

    private CompanyCapability fromPayload(Map<String, Object> c) {
        String kind = str(c.get("kind"), "OTHER");
        Map<String, Object> attrs = c.get("attributes") instanceof Map<?, ?> map
            ? (Map<String, Object>) map : Map.of();
        return new CompanyCapability(
            str(c.get("capabilityId"), str(c.get("id"), UUID.randomUUID().toString())),
            str(c.get("organizationId"), ""),
            CapabilityKind.valueOf(kind),
            str(c.get("canonicalKey"), "other"),
            str(c.get("label"), str(c.get("canonicalKey"), "other")),
            attrs,
            str(c.get("validFrom"), null),
            str(c.get("validTo"), null),
            str(c.get("validityStatus"), "UNKNOWN"),
            str(c.get("sourceDocumentId"), null),
            str(c.get("evidenceSnippet"), null),
            c.get("confidence") instanceof Number n ? n.doubleValue() : 0.8
        );
    }

    private static UUID parseUuid(Object value, UUID fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // accept dd.MM.yyyy or ISO
        try {
            if (value.contains(".")) {
                String[] p = value.split("\\.");
                if (p.length == 3) {
                    String y = p[2].length() == 2 ? "20" + p[2] : p[2];
                    return Date.valueOf(y + "-" + p[1] + "-" + p[0]);
                }
            }
            return Date.valueOf(value.substring(0, Math.min(10, value.length())));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
