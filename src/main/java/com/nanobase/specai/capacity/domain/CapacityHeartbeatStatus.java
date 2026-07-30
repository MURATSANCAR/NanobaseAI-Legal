package com.nanobase.specai.capacity.domain;

public enum CapacityHeartbeatStatus {
    UPDATED,
    LEASE_LOST,
    LEASE_EXPIRED,
    NOT_FOUND,
    PROVIDER_UNAVAILABLE
}
