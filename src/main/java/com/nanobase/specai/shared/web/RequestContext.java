package com.nanobase.specai.shared.web;

import java.util.UUID;

public final class RequestContext {
    private static final ThreadLocal<RequestMetadata> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(RequestMetadata metadata) {
        CURRENT.set(metadata);
    }

    public static RequestMetadata current() {
        RequestMetadata metadata = CURRENT.get();
        return metadata == null
            ? new RequestMetadata(UUID.randomUUID(), null, null)
            : metadata;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record RequestMetadata(UUID correlationId, String ipAddress, String userAgent) {
    }
}
