package com.nanobase.specai.capacity.domain;

public enum CapacityReleaseStatus {
    RELEASED,
    ALREADY_RELEASED,
    STALE_LEASE,
    NOT_FOUND,
    PROVIDER_UNAVAILABLE
}
