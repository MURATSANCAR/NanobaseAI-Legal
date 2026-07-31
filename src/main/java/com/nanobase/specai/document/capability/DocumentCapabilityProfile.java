package com.nanobase.specai.document.capability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Policy-driven document capability profile. Concept codes are open-ended strings
 * (not closed enums) so new content modes can be added without code changes.
 */
public record DocumentCapabilityProfile(
    UUID id,
    UUID organizationId,
    UUID documentVersionId,
    String formatConceptCode,
    String contentModeConceptCode,
    String layoutComplexityConceptCode,
    String ocrNeedConceptCode,
    BigDecimal tableDensity,
    BigDecimal imageDensity,
    BigDecimal textDensity,
    BigDecimal headingConfidence,
    Map<String, Object> languageProfile,
    int pageCount,
    int estimatedTokenCount,
    String recommendedParserProfileCode,
    String recommendedOcrProfileCode,
    String recommendedSegmentationProfileCode,
    String recommendedExtractionProfileCode,
    Instant createdAt
) {
}
