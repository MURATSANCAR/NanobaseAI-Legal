package com.nanobase.specai.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class MeasurableFieldsEnricherTest {
    private final MeasurableFieldsEnricher enricher =
        new MeasurableFieldsEnricher(new ObjectMapper());

    @Test
    void autoResolvesNumericQuantity() {
        var extraction = enricher.extract("Yüklenici en az 16 adet DIMM sağlayacaktır.");
        assertThat(extraction.autoResolvable()).isTrue();
        assertThat(extraction.operator()).isEqualTo(">=");
        assertThat(extraction.threshold()).isEqualTo("16");

        ObjectNode empty = new ObjectMapper().createObjectNode();
        var enrichment = enricher.enrich(
            "Yüklenici en az 16 adet DIMM sağlayacaktır.", empty, "TECHNICAL", "MUST");
        assertThat(enrichment.changed()).isTrue();
        assertThat(enrichment.missingFields()).isEmpty();
        assertThat(enrichment.attributes().path("ambiguityStatus").asText())
            .isEqualTo("RESOLVED_STRUCTURED");
    }

    @Test
    void keepsQualitativeAsHighCandidateWithSuggestions() {
        var enrichment = enricher.enrich(
            "Teklif edilen ürünler IPv6 için uygun olacaktır.",
            new ObjectMapper().createObjectNode(),
            "TECHNICAL",
            "MUST");
        assertThat(enrichment.changed()).isFalse();
        assertThat(enrichment.missingFields()).contains("missingAcceptanceThreshold");
        assertThat(enrichment.priority()).isEqualTo("HIGH");
        assertThat(enrichment.extraction().suggestedFields())
            .containsEntry("measurement", "presence");
        assertThat(enricher.describe(enrichment.missingFields(), enrichment.priority(),
            enrichment.extraction().suggestedFields())).startsWith("[HIGH]");
    }
}
