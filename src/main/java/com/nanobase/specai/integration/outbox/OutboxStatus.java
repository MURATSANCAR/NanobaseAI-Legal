package com.nanobase.specai.integration.outbox;

public enum OutboxStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    FAILED,
    DEAD
}
