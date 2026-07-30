package com.nanobase.specai.compliance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ComplianceTaskPersistenceFencingTest {
    @Test
    void persistRejectsStaleLeaseGeneration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantDatabaseContext tenant = mock(TenantDatabaseContext.class);
        ObjectMapper mapper = new ObjectMapper();
        OutboxService outbox = mock(OutboxService.class);
        PlatformMetrics metrics = mock(PlatformMetrics.class);
        ComplianceJobTransactionService txs = mock(ComplianceJobTransactionService.class);
        CompliancePostAssessmentHooks hooks = mock(CompliancePostAssessmentHooks.class);
        when(txs.cancellationState(any(), any()))
            .thenReturn(new ComplianceJobTransactionService.CancellationSnapshot(
                false, null, "RUNNING"));
        when(jdbc.update(contains("status = 'COMPLETED'"), any(), any(), any(), any(), any(),
            any(), any(), anyLong())).thenReturn(0);

        ComplianceTaskPersistenceService service = new ComplianceTaskPersistenceService(
            jdbc, tenant, mapper, outbox, metrics, txs, hooks);
        PreparedComplianceTask prepared = new PreparedComplianceTask(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, "worker-a", 1L, UUID.randomUUID().toString(), "BALANCED",
            1, 1, new RankedEvidenceResult(List.of(), 0),
            new PreparedComplianceTask.RequirementSnapshot(
                UUID.randomUUID(), "R1", "text", mapper.createObjectNode(), null),
            new PreparedComplianceTask.JobPolicySnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            PreparedComplianceTask.ExecutionPlan.PRECOMPUTED,
            null, List.of(), mapper.createObjectNode(), List.of(), false,
            new PreparedComplianceTask.PrecomputedOutcome(
                UUID.randomUUID(), "COMPLIANT", mapper.createObjectNode(), 0.9, false,
                null, List.of(), false, null, "DETERMINISTIC", List.of(), false, null));
        ComplianceExecutionResult execution =
            ComplianceExecutionResult.fromPrecomputed(prepared.precomputedOutcome());

        var result = service.persist(prepared, execution, UUID.randomUUID());
        assertTrue(result.rejectedStale());
        assertEquals("STALE_WORKER_RESULT", result.errorCode());
    }
}
