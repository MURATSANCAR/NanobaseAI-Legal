package com.nanobase.specai.compliance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.ClaimOutcome;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobClaimResult;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class ComplianceJobTransactionServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void claimJobReturnsClaimedWhenUpdateReturningSucceeds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantDatabaseContext tenant = mock(TenantDatabaseContext.class);
        ComplianceJobTransactionService service =
            new ComplianceJobTransactionService(jdbc, tenant);
        UUID orgId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant lease = Instant.now().plusSeconds(900);
        JobClaimResult expected = new JobClaimResult(
            ClaimOutcome.CLAIMED, jobId, projectId, "RUNNING", 1,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 1, 1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
            .thenReturn(List.of(expected));

        JobClaimResult result = service.claimJob(orgId, jobId, "worker-a", lease);

        assertTrue(result.claimed());
        assertEquals(ClaimOutcome.CLAIMED, result.outcome());
        verify(jdbc).query(contains("status = 'RUNNING'"), any(RowMapper.class),
            any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimJobDiagnosesAlreadyClaimedWithoutMarkingLlmUnavailable() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantDatabaseContext tenant = mock(TenantDatabaseContext.class);
        ComplianceJobTransactionService service =
            new ComplianceJobTransactionService(jdbc, tenant);
        UUID orgId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(), any()))
            .thenAnswer(invocation -> {
                ResultSetExtractor<JobClaimResult> extractor = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getString("status")).thenReturn("RUNNING");
                when(rs.getString("claimed_by")).thenReturn("other-worker");
                when(rs.getTimestamp("lease_expires_at"))
                    .thenReturn(Timestamp.from(Instant.now().plusSeconds(600)));
                when(rs.getTimestamp("claimed_at"))
                    .thenReturn(Timestamp.from(Instant.now()));
                when(rs.getInt("total_requirement_count")).thenReturn(1);
                when(rs.getInt("attempt_count")).thenReturn(1);
                when(rs.getObject(anyString(), ArgumentMatchers.eq(UUID.class)))
                    .thenReturn(UUID.randomUUID());
                return extractor.extractData(rs);
            });

        JobClaimResult result = service.claimJob(orgId, jobId, "worker-a",
            Instant.now().plusSeconds(900));

        assertFalse(result.claimed());
        assertEquals(ClaimOutcome.CLAIMED_BY_OTHER_WORKER, result.outcome());
        assertEquals("JOB_ALREADY_CLAIMED",
            ComplianceJobTransactionService.orchestrationErrorCode(result.outcome()));
    }

    @Test
    void finalizeJobIsIdempotentWhenAlreadyTerminal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantDatabaseContext tenant = mock(TenantDatabaseContext.class);
        ComplianceJobTransactionService service =
            new ComplianceJobTransactionService(jdbc, tenant);
        UUID orgId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(), any()))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                ResultSetExtractor<?> extractor = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.next()).thenReturn(true);
                if (sql.contains("count(*) filter")) {
                    when(rs.getInt("completed")).thenReturn(1);
                    when(rs.getInt("failed")).thenReturn(0);
                    when(rs.getInt("cancelled")).thenReturn(0);
                    when(rs.getInt("active")).thenReturn(0);
                    when(rs.getInt("retry_waiting")).thenReturn(0);
                    when(rs.getInt("total")).thenReturn(1);
                } else {
                    when(rs.getString("status")).thenReturn("COMPLETED");
                    when(rs.getTimestamp("cancel_requested_at")).thenReturn(null);
                }
                return extractor.extractData(rs);
            });
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        var result = service.finalizeJob(orgId, jobId);
        assertEquals("COMPLETED", result.status());
    }
}
