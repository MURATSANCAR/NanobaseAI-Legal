package com.nanobase.specai.capacity.domain;

import java.time.Instant;

public record CapacityHeartbeatResult(
    CapacityHeartbeatStatus status,
    Instant expiresAt
) {
}
