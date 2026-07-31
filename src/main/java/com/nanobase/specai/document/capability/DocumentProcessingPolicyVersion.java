package com.nanobase.specai.document.capability;

import java.util.Map;

public record DocumentProcessingPolicyVersion(
    String policyCode,
    String version,
    Map<String, Object> configuration
) {
}
