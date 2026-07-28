package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface ReportFormatRenderer {
    boolean supports(String formatConceptCode);

    RenderedReport render(String reportName, JsonNode structuredReport);

    record RenderedReport(byte[] content, String extension, String mimeType) {
    }
}
