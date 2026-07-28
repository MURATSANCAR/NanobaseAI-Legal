package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

@Component
public class GenericSnapshotSectionDataProvider implements ReportSectionDataProvider {
    private final ObjectMapper mapper;

    public GenericSnapshotSectionDataProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sectionTypeConceptCode) {
        return sectionTypeConceptCode != null && !sectionTypeConceptCode.isBlank();
    }

    @Override
    public JsonNode load(ReportSectionContext context) {
        JsonNode snapshot = mapper.valueToTree(context.verifiedSnapshot());
        String pointer = context.sectionConfiguration().path("snapshotJsonPointer")
            .asText("");
        if (pointer.isBlank()) {
            return snapshot;
        }
        if (!pointer.startsWith("/") || pointer.contains("..")) {
            throw new IllegalArgumentException("Unsafe report snapshot JSON pointer");
        }
        JsonNode selected = snapshot.at(pointer);
        return selected.isMissingNode() ? JsonNodeFactory.instance.nullNode() : selected;
    }
}
