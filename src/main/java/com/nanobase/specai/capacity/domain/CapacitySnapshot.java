package com.nanobase.specai.capacity.domain;

import java.util.List;

public record CapacitySnapshot(
    String modelProfile,
    int activeLeaseCount,
    int maxConcurrency,
    List<CapacityLease> leases
) {
}
