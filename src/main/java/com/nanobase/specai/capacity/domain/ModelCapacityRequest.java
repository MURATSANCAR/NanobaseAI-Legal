package com.nanobase.specai.capacity.domain;

import java.time.Duration;
import java.util.UUID;

public record ModelCapacityRequest(
    String modelProfile,
    UUID organizationId,
    UUID jobId,
    UUID taskId,
    String workerId,
    Duration waitTimeout,
    Duration leaseDuration,
    String correlationId
) {
}
