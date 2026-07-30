package com.nanobase.specai.capacity.domain;

public enum CapacityAcquireStatus {
    ACQUIRED,
    CAPACITY_FULL,
    WAIT_TIMEOUT,
    CANCELLED,
    PROVIDER_UNAVAILABLE
}
