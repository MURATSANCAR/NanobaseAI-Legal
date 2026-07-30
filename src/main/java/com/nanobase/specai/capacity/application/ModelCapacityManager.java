package com.nanobase.specai.capacity.application;

import com.nanobase.specai.capacity.domain.CapacityAcquireResult;
import com.nanobase.specai.capacity.domain.CapacityHeartbeatResult;
import com.nanobase.specai.capacity.domain.CapacityLease;
import com.nanobase.specai.capacity.domain.CapacityReleaseResult;
import com.nanobase.specai.capacity.domain.CapacitySnapshot;
import com.nanobase.specai.capacity.domain.ModelCapacityRequest;

/**
 * Provider-neutral global model capacity port.
 *
 * <p>Production multi-instance deployments must use a distributed implementation
 * (for example Redis lease-backed). Process-local semaphores are not a valid
 * substitute for multi-orchestrator capacity guarantees.
 */
public interface ModelCapacityManager {

    CapacityAcquireResult acquire(ModelCapacityRequest request);

    CapacityHeartbeatResult heartbeat(CapacityLease lease);

    CapacityReleaseResult release(CapacityLease lease);

    CapacitySnapshot snapshot(String modelProfile);
}
