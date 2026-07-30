package com.nanobase.specai.capacity.domain;

/**
 * Behavior when the global capacity provider cannot be reached.
 * Production default must be {@link #FAIL_CLOSED}.
 */
public enum CapacityFailurePolicy {
    FAIL_CLOSED,
    FALLBACK_TO_MODEL_GATEWAY,
    SINGLE_INSTANCE_LOCAL_FALLBACK,
    MANUAL_OVERRIDE
}
