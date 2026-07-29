package com.nanobase.specai.document.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FileSecurityAssessmentRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public FileSecurityAssessmentRecorder(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID organizationId, UUID projectId, String sha256,
                       FileSecurityScanner.ScanResult result, Map<String, Object> preflightSignals,
                       UUID correlationId) {
        jdbc.queryForObject("select set_config('app.current_organization_id', ?, true)",
            String.class, organizationId.toString());
        Map<String, Object> signals = new java.util.LinkedHashMap<>(preflightSignals);
        signals.putAll(result.signals());
        jdbc.update("""
            insert into file_security_assessment (
                id, organization_id, project_id, content_sha256, scanner, scanner_version,
                status, signals_json, correlation_id, assessed_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """, UUID.randomUUID(), organizationId, projectId, sha256, result.scanner(),
            result.scannerVersion(), result.status().name(), json(signals), correlationId,
            Timestamp.from(clock.instant()));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Security signals cannot be serialized", exception);
        }
    }
}
