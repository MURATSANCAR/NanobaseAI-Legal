package com.nanobase.specai.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import com.nanobase.specai.analysis.domain.RequirementConditionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequirementConditionExtractorTest {
    @Mock
    private RequirementConditionRepository repository;

    @Test
    void splitsIsoListIntoThreeExistsConditions() {
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var extractor = new RequirementConditionExtractor(repository);
        var result = extractor.extractAndPersist(UUID.randomUUID(), UUID.randomUUID(),
            "ISO 27001, ISO 22301 ve PCI DSS belgelerine sahip olunmalıdır.", null);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.conditions()).hasSize(3);
        assertThat(result.conditions())
            .extracting(RequirementCondition::expectedValue)
            .contains("ISO 27001", "ISO 22301", "PCI DSS");
        assertThat(result.conditions())
            .allMatch(item -> item.operator() == ConditionOperator.EXISTS);
    }

    @Test
    void extractsDistanceThreshold() {
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var extractor = new RequirementConditionExtractor(repository);
        var result = extractor.extractAndPersist(UUID.randomUUID(), UUID.randomUUID(),
            "Veri merkezleri arasında en az 350 km mesafe bulunmalıdır.", null);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.conditions()).hasSize(1);
        RequirementCondition condition = result.conditions().getFirst();
        assertThat(condition.fieldName()).isEqualTo("DATA_CENTER_DISTANCE");
        assertThat(condition.operator()).isEqualTo(ConditionOperator.GREATER_THAN_OR_EQUAL);
        assertThat(condition.expectedNumericValue()).isEqualByComparingTo("350");
        assertThat(condition.expectedUnit()).isEqualToIgnoringCase("km");
        ArgumentCaptor<List<RequirementCondition>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
