package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

public interface ReportSectionDataProvider {
    boolean supports(String sectionTypeConceptCode);

    JsonNode load(ReportSectionContext context);

    record ReportSectionContext(
        UUID organizationId,
        UUID projectId,
        UUID dataSnapshotId,
        JsonNode sectionConfiguration,
        Map<String, Object> verifiedSnapshot
    ) {
    }
}
