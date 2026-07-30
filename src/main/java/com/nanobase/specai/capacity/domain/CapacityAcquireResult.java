package com.nanobase.specai.capacity.domain;

public record CapacityAcquireResult(
    CapacityAcquireStatus status,
    CapacityLease lease,
    long waitMillis
) {
    public static CapacityAcquireResult acquired(CapacityLease lease, long waitMillis) {
        return new CapacityAcquireResult(CapacityAcquireStatus.ACQUIRED, lease, waitMillis);
    }

    public static CapacityAcquireResult of(CapacityAcquireStatus status, long waitMillis) {
        return new CapacityAcquireResult(status, null, waitMillis);
    }
}
