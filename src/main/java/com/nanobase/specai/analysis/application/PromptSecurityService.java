package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PromptSecurityService {
    private final RestClient client;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    public PromptSecurityService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8092}") String baseUrl,
        @Value("${specai.ai-orchestrator.connect-timeout:PT5S}") Duration connectTimeout,
        @Value("${specai.ai-orchestrator.prompt-security-read-timeout:PT30S}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Assessment assess(UUID organizationId, UUID documentVersionId, UUID clauseId,
                             UUID analysisProfileId, UUID correlationId,
                             Map<String, Object> untrustedContext) {
        Assessment assessment;
        try {
            assessment = client.post()
                .uri("/v1/prompt-security/assess")
                .header("X-Correlation-ID", correlationId.toString())
                .body(Map.of("untrustedContext", untrustedContext))
                .retrieve()
                .body(Assessment.class);
        } catch (RuntimeException unavailable) {
            assessment = new Assessment("ASSESSMENT_FAILED", 1d,
                List.of("assessment_unavailable"), "PENDING");
        }
        if (assessment == null) {
            assessment = new Assessment("ASSESSMENT_FAILED", 1d,
                List.of("empty_assessment"), "PENDING");
        }
        jdbc.update("""
            insert into prompt_security_assessment (
                id, organization_id, document_version_id, clause_id,
                assessment_profile_id, signal_score, signals_json, review_status, created_at
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
            """, UUID.randomUUID(), organizationId, documentVersionId, clauseId,
            analysisProfileId, assessment.signalScore(),
            json(Map.of("status", assessment.status(), "signals", assessment.signals())),
            assessment.reviewStatus(), Timestamp.from(clock.instant()));
        return assessment;
    }

    private String json(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                "Prompt security signals cannot be serialized", exception);
        }
    }

    public record Assessment(String status, double signalScore, List<String> signals,
                             String reviewStatus) {
    }
}
