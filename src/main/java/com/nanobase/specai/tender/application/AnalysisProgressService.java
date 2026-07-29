package com.nanobase.specai.tender.application;

import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.AnalysisProgressCounts;
import com.nanobase.specai.tender.api.TenderContracts.AnalysisProgressResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisProgressService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;

    public AnalysisProgressService(JdbcTemplate jdbc, ProjectAccessService access,
                                   CurrentTenant currentTenant) {
        this.jdbc = jdbc;
        this.access = access;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public AnalysisProgressResponse progress(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        UUID orgId = principal.tenantId();

        long readyDocuments = count("""
            select count(*) from document
             where organization_id = ? and project_id = ?
               and status = 'READY' and included_in_analysis = true
            """, orgId, projectId);
        long requirements = count("""
            select count(*) from requirement
             where organization_id = ? and project_id = ?
            """, orgId, projectId);
        long knowledgeEntities = count("""
            select count(*) from knowledge_entity
             where organization_id = ? and valid_until is null
            """, orgId);
        long complianceEvaluations = count("""
            select count(*) from compliance_evaluation
             where organization_id = ? and project_id = ?
            """, orgId, projectId);
        long risks = count("""
            select count(*) from risk_record
             where organization_id = ? and project_id = ?
            """, orgId, projectId);

        boolean documentsDone = readyDocuments > 0;
        boolean requirementsDone = requirements > 0;
        boolean knowledgeDone = knowledgeEntities > 0;
        boolean complianceDone = complianceEvaluations > 0;
        boolean risksDone = risks > 0;

        return new AnalysisProgressResponse(
            projectId,
            documentsDone,
            requirementsDone,
            knowledgeDone,
            complianceDone,
            risksDone,
            recommendedStep(documentsDone, requirementsDone, knowledgeDone, complianceDone,
                risksDone),
            new AnalysisProgressCounts(
                readyDocuments,
                requirements,
                knowledgeEntities,
                complianceEvaluations,
                risks
            )
        );
    }

    private static String recommendedStep(boolean documents, boolean requirements,
                                          boolean knowledge, boolean compliance, boolean risks) {
        if (!documents) {
            return "documents";
        }
        if (!requirements) {
            return "requirements";
        }
        if (!knowledge) {
            return "knowledge";
        }
        if (!compliance) {
            return "compliance";
        }
        if (!risks) {
            return "risks";
        }
        return "risks";
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
