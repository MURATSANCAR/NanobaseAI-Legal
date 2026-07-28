package com.nanobase.specai.document.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.nanobase.specai.document.application.ExternalDocumentMappingService;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentProcessingCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DocumentIntelligenceAdapterContractTest {
    @Test
    void doclingSubmissionStatusAndResultUseProviderNeutralContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID jobId = UUID.randomUUID();
        DocumentProcessingCommand command = command();
        ProviderCircuitBreaker breaker = breaker(3);
        DoclingDocumentIntelligenceAdapter adapter =
            new DoclingDocumentIntelligenceAdapter(builder, breaker,
                "http://docling.test", "specai-original");
        server.expect(requestTo("http://docling.test/v1/documents/parse"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"jobId\":\"" + jobId + "\",\"status\":\"QUEUED\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://docling.test/v1/jobs/" + jobId))
            .andRespond(withSuccess(
                "{\"jobId\":\"" + jobId + "\",\"status\":\"COMPLETED\","
                    + "\"currentStage\":\"READY\",\"progress\":100,"
                    + "\"message\":\"ready\",\"errorCode\":null}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://docling.test/v1/jobs/" + jobId + "/result"))
            .andRespond(withSuccess(resultJson(command.documentVersionId()),
                MediaType.APPLICATION_JSON));

        var submission = adapter.submit(command);
        var status = adapter.getStatus(submission.reference());
        var result = adapter.getResult(submission.reference());

        assertThat(submission.status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(status.status()).isEqualTo(DocumentStatus.READY);
        assertThat(result.provider()).isEqualTo("DOCLING");
        assertThat(result.documentVersionId()).isEqualTo(command.documentVersionId());
        server.verify();
    }

    @Test
    void invalidSchemaAnd429OpenCircuitBreaker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DoclingDocumentIntelligenceAdapter adapter =
            new DoclingDocumentIntelligenceAdapter(builder, breaker(1),
                "http://docling.test", "specai-original");
        server.expect(requestTo("http://docling.test/v1/documents/parse"))
            .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> adapter.submit(command()))
            .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> adapter.submit(command()))
            .isInstanceOf(ProviderUnavailableException.class)
            .hasMessageContaining("circuit breaker");
        server.verify();
    }

    @Test
    void openContractsCreatesCorpusStoresMappingAndReturnsExternalId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalDocumentMappingService mappings =
            mock(ExternalDocumentMappingService.class);
        when(mappings.find(any(), any(), any())).thenReturn(Optional.empty());
        OpenContractsDocumentIntelligenceAdapter adapter =
            new OpenContractsDocumentIntelligenceAdapter(builder, breaker(3), mappings,
                "http://opencontracts.test", "token");
        DocumentProcessingCommand command = command();
        server.expect(requestTo("http://opencontracts.test/api/v1/corpora"))
            .andRespond(withSuccess("{\"id\":\"corpus-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "http://opencontracts.test/api/v1/corpora/corpus-1/documents"))
            .andRespond(withSuccess(
                "{\"documentId\":\"external-1\",\"status\":\"QUEUED\","
                    + "\"providerVersion\":\"1.0\"}",
                MediaType.APPLICATION_JSON));

        var submission = adapter.submit(command);

        assertThat(submission.reference().externalReference()).isEqualTo("external-1");
        verify(mappings).submitted(eq(command.organizationId()),
            eq(command.documentVersionId()), any(), eq("corpus-1"),
            eq("external-1"), eq("1.0"));
        server.verify();
    }

    private ProviderCircuitBreaker breaker(int threshold) {
        return new ProviderCircuitBreaker(threshold, Duration.ofMinutes(1),
            Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
    }

    private DocumentProcessingCommand command() {
        UUID organizationId = UUID.randomUUID();
        return new DocumentProcessingCommand(organizationId, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(),
            "specai-original/" + organizationId + "/project/document/version/spec.pdf",
            "spec.pdf", "application/pdf", "a".repeat(64), "tr", false,
            UUID.randomUUID(), 100, false);
    }

    private String resultJson(UUID versionId) {
        return """
            {
              "documentVersionId":"%s",
              "provider":"DOCLING",
              "providerVersion":"2.43.0",
              "pageCount":1,
              "language":"tr",
              "textQualityScore":0.95,
              "pages":[],
              "clauses":[],
              "tables":[],
              "warnings":[],
              "metadata":{}
            }
            """.formatted(versionId);
    }
}
