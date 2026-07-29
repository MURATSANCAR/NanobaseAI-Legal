package com.nanobase.specai.compliance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanobase.specai.compliance.application.SemanticEvaluationFailureCode;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

class HttpComplianceAiGatewayTest {
    @Test
    void classifiesReadTimeoutAsLlmTimeout() {
        ResourceAccessException failure = new ResourceAccessException(
            "I/O error on POST request for \"http://ai-orchestrator:8090/v1/compliance-evaluations\": Request timed out",
            new SocketTimeoutException("Request timed out"));

        assertThat(HttpComplianceAiGateway.classify(failure))
            .isEqualTo(SemanticEvaluationFailureCode.LLM_TIMEOUT);
        assertThat(HttpComplianceAiGateway.retryable(SemanticEvaluationFailureCode.LLM_TIMEOUT))
            .isTrue();
    }

    @Test
    void classifiesTransientServerErrorsAsUnavailableAndRetryable() {
        assertThat(HttpComplianceAiGateway.classify(
            HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway",
                null, null, null)))
            .isEqualTo(SemanticEvaluationFailureCode.LLM_UNAVAILABLE);
        assertThat(HttpComplianceAiGateway.classify(
            HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "busy",
                null, null, null)))
            .isEqualTo(SemanticEvaluationFailureCode.LLM_UNAVAILABLE);
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_UNAVAILABLE)).isTrue();
    }

    @Test
    void doesNotRetryInvalidStructuredResponses() {
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE)).isFalse();
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_CONTEXT_OVERFLOW)).isFalse();
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_GENERATION_TIMEOUT)).isFalse();
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_TIMEOUT, true, true)).isTrue();
        assertThat(HttpComplianceAiGateway.retryable(
            SemanticEvaluationFailureCode.LLM_TIMEOUT, false, true)).isFalse();
        assertThat(HttpComplianceAiGateway.deploymentAlias("FAST"))
            .isEqualTo("nanobase-fast");
        assertThat(HttpComplianceAiGateway.deploymentAlias("BALANCED"))
            .isEqualTo("nanobase-balanced");
    }

    @Test
    void failureCodesRemainDistinctFromComplianceDecisions() {
        assertThat(SemanticEvaluationFailureCode.LLM_TIMEOUT.name())
            .isNotEqualTo("INSUFFICIENT_INFORMATION");
        assertThatThrownBy(() -> {
            throw new com.nanobase.specai.compliance.application.SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_TIMEOUT, "timed out", 1);
        }).isInstanceOf(com.nanobase.specai.compliance.application.SemanticEvaluationException.class)
            .extracting(ex -> ((com.nanobase.specai.compliance.application.SemanticEvaluationException) ex)
                .failureCode())
            .isEqualTo(SemanticEvaluationFailureCode.LLM_TIMEOUT);
    }
}
