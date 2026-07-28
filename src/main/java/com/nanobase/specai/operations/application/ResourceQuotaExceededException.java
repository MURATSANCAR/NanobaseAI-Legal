package com.nanobase.specai.operations.application;

public class ResourceQuotaExceededException extends RuntimeException {
    private final String quotaCode;
    private final long limit;
    private final long currentUsage;

    public ResourceQuotaExceededException(String quotaCode, long limit, long currentUsage) {
        super("%s quota exceeded (limit=%d, requested usage=%d)"
            .formatted(quotaCode, limit, currentUsage));
        this.quotaCode = quotaCode;
        this.limit = limit;
        this.currentUsage = currentUsage;
    }

    public String quotaCode() {
        return quotaCode;
    }

    public long limit() {
        return limit;
    }

    public long currentUsage() {
        return currentUsage;
    }
}
