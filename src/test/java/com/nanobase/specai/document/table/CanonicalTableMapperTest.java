package com.nanobase.specai.document.table;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalTableMapperTest {

    @Test
    void preservesMarkdownHeaderContext() {
        CanonicalTable table = CanonicalTableMapper.fromStructured(
            "DOCLING",
            0,
            1,
            1,
            "IP table",
            Map.of(),
            """
                | Parametre | Minimum Değer | Açıklama |
                | --- | --- | --- |
                | IP koruma sınıfı | IP65 | Dış ortam |
                """
        );
        assertThat(table.cells()).isNotEmpty();
        assertThat(table.headerRows()).isNotEmpty();
        CanonicalTableCell value = table.cells().stream()
            .filter(c -> c.textContent().contains("IP65"))
            .findFirst()
            .orElseThrow();
        assertThat(value.headerContext()).containsEntry("column", "Minimum Değer");
    }

    @Test
    void mapsGridCellsWithoutFormatBranch() {
        CanonicalTable table = CanonicalTableMapper.fromStructured(
            "DOCLING",
            1,
            2,
            2,
            null,
            Map.of("grid_cells", List.of(
                Map.of("row_index", 0, "column_index", 0, "text", "Parametre",
                    "column_header", true),
                Map.of("row_index", 0, "column_index", 1, "text", "Değer",
                    "column_header", true),
                Map.of("row_index", 1, "column_index", 0, "text", "Sıcaklık"),
                Map.of("row_index", 1, "column_index", 1, "text", "40 C")
            )),
            null
        );
        assertThat(table.cells()).hasSize(4);
        assertThat(table.headerRows().get(0)).contains("Parametre", "Değer");
    }
}
