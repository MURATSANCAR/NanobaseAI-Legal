package com.nanobase.specai.document.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeaderContextTableRequirementExtractionStrategyTest {

    private final HeaderContextTableRequirementExtractionStrategy strategy =
        new HeaderContextTableRequirementExtractionStrategy();

    @Test
    void preservesHeaderContextInsteadOfIsolatedCellValue() {
        CanonicalTable table = new CanonicalTable(
            "test", 0, 1, 1, "Koruma", null,
            List.of(List.of("Parametre", "Minimum Deger", "Aciklama")),
            List.of(new CanonicalTableCell(
                1, 1, 1, 1, "IP65", "IP65",
                Map.of("Parametre", "IP koruma sinifi", "Aciklama", "Dis ortam"),
                List.of())),
            0.9d, Map.of());
        TableRequirementExtractionResult result = strategy.extract(
            table, new TableExtractionProfile("default", true, 50));
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().statement())
            .contains("IP koruma")
            .contains("IP65")
            .doesNotContain("=> IP65 =>");
        assertThat(result.candidates().getFirst().statement()).contains("=>");
    }

    @Test
    void skipsIsolatedShortTokenWithoutHeadersWhenRequired() {
        CanonicalTable table = new CanonicalTable(
            "test", 0, 1, 1, null, null, List.of(),
            List.of(new CanonicalTableCell(0, 0, 1, 1, "IP65", "IP65", Map.of(), List.of())),
            0.5d, Map.of());
        TableRequirementExtractionResult result = strategy.extract(
            table, new TableExtractionProfile("default", true, 50));
        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("HEADER_CONTEXT_MISSING"));
    }
}
