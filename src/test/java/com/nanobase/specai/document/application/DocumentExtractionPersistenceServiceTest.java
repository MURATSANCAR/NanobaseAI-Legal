package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.DocumentPageRepository;
import com.nanobase.specai.document.domain.DocumentTableRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.ParserWarningRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentExtractionPersistenceServiceTest {
    private final UUID organizationId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
    private final DocumentPageRepository pages = mock(DocumentPageRepository.class);
    private final ClauseRepository clauses = mock(ClauseRepository.class);
    private final DocumentTableRepository tables = mock(DocumentTableRepository.class);
    private final ParserWarningRepository warnings = mock(ParserWarningRepository.class);
    private final TenantDatabaseContext tenant = mock(TenantDatabaseContext.class);
    private DocumentExtractionPersistenceService service;

    @BeforeEach
    void setup() {
        DocumentVersion version = new DocumentVersion(versionId, organizationId,
            UUID.randomUUID(), 1, "key", "spec.pdf", "application/pdf", 100,
            "a".repeat(64), "user", Instant.now());
        when(versions.findForUpdate(versionId, organizationId))
            .thenReturn(Optional.of(version));
        service = new DocumentExtractionPersistenceService(
            versions, pages, clauses, tables, warnings, tenant, new ObjectMapper(),
            new PlatformMetrics(new SimpleMeterRegistry(),
                mock(OutboxEventRepository.class)),
            mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @Test
    void persistsParentBeforeChildUsingProviderSourceIds() {
        service.persist(organizationId, UUID.randomUUID(),
            result(List.of(
                clause("child", "parent", 2, "2"),
                clause("parent", null, 1, "1")
            )), false);

        verify(clauses, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void rejectsMissingClauseParent() {
        assertThatThrownBy(() -> service.persist(organizationId, UUID.randomUUID(),
            result(List.of(clause("child", "missing", 1, "1"))), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parent");
    }

    @Test
    void uniquifiesDuplicateAndBlankClauseNumbers() {
        service.persist(organizationId, UUID.randomUUID(),
            result(List.of(
                clause("a", null, 1, "15.11"),
                clause("b", null, 2, "15.11"),
                clause("c", null, 3, ""),
                clause("d", null, 4, null)
            )), false);

        ArgumentCaptor<Clause> captor = ArgumentCaptor.forClass(Clause.class);
        verify(clauses, org.mockito.Mockito.times(4)).save(captor.capture());
        List<String> numbers = captor.getAllValues().stream()
            .map(Clause::clauseNumber)
            .toList();
        assertThat(numbers).containsExactly("15.11", "15.11#2", "C3", "C4");
        assertThat(new HashSet<>(numbers)).hasSize(4);
    }

    @Test
    void uniqueClauseNumberHelperKeepsOriginalThenSuffixes() {
        Set<String> used = new HashSet<>();
        assertThat(DocumentExtractionPersistenceService.uniqueClauseNumber("1", 1, used))
            .isEqualTo("1");
        assertThat(DocumentExtractionPersistenceService.uniqueClauseNumber("1", 2, used))
            .isEqualTo("1#2");
        assertThat(DocumentExtractionPersistenceService.uniqueClauseNumber(" ", 9, used))
            .isEqualTo("C9");
    }

    private DocumentExtractionResult result(List<ExtractedClause> extractedClauses) {
        return new DocumentExtractionResult(versionId, "DOCLING", "2.43.0",
            1, "tr", .9, List.of(), extractedClauses, List.of(), List.of(), Map.of());
    }

    private ExtractedClause clause(String sourceId, String parentId, int sortOrder,
                                   String clauseNumber) {
        return new ExtractedClause(sourceId, parentId, clauseNumber,
            "Başlık", "Metin", "Metin", "SECTION", 1, 1, List.of(),
            null, sortOrder, Map.of());
    }
}
