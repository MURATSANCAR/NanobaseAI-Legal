package com.nanobase.specai.capacity.domain;

import java.time.Instant;

public record CapacityLease(
    String leaseId,
    String modelProfile,
    String ownerId,
    long generation,
    Instant acquiredAt,
    Instant expiresAt
) {
}
