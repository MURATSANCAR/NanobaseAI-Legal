package com.nanobase.specai.operations.application;

public class WorkloadCapacityException extends RuntimeException {
    private final BackpressureService.Decision decision;

    public WorkloadCapacityException(BackpressureService.Decision decision) {
        super("Workload capacity is temporarily unavailable; retry later");
        this.decision = decision;
    }

    public BackpressureService.Decision decision() {
        return decision;
    }
}
