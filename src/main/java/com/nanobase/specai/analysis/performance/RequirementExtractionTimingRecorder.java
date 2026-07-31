package com.nanobase.specai.analysis.performance;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Stage timing recorder for requirement extraction. Does not store raw text.
 */
@Service
public class RequirementExtractionTimingRecorder {
    private final JdbcTemplate jdbc;

    public RequirementExtractionTimingRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
        UUID organizationId,
        UUID jobId,
        UUID taskId,
        UUID clauseId,
        UUID chunkId,
        String stageConceptCode,
        long durationMs,
        Long queueWaitMs,
        Long capacityWaitMs,
        Integer inputTokens,
        Integer outputTokens,
        String modelProfile
    ) {
        jdbc.update("""
            insert into requirement_extraction_timing (
                id, organization_id, job_id, task_id, clause_id, chunk_id,
                stage_concept_code, duration_ms, queue_wait_ms, capacity_wait_ms,
                input_tokens, output_tokens, model_profile, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """,
            UUID.randomUUID(), organizationId, jobId, taskId, clauseId, chunkId,
            stageConceptCode, durationMs, queueWaitMs, capacityWaitMs,
            inputTokens, outputTokens, modelProfile);
    }
}
