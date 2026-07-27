package com.nanobase.specai.document.integration;

import java.util.UUID;

public record DocumentRoutingContext(
    UUID organizationId,
    String mimeType,
    String fileExtension,
    long fileSize,
    Integer pageCount,
    Double digitalTextRatio,
    boolean scanLikely,
    Double tableDensity,
    String language,
    boolean doclingAvailable,
    boolean openContractsAvailable,
    boolean annotationSynchronizationRequested,
    String tenantPreferredProvider
) {
}
