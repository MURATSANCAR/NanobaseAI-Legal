package com.nanobase.specai.document.delivery;

import java.time.Instant;
import java.util.UUID;

public record ObjectDeliveryContext(
    UUID organizationId,
    UUID subjectId,
    String subjectType,
    String objectKey,
    String contentType,
    Instant expiresAt,
    ObjectDeliveryMode preferredMode
) {
}
