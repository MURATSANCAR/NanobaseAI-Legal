package com.nanobase.specai.document.delivery;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deployment-profile driven delivery. Never returns Docker-internal hostnames
 * to browsers. Falls back to BACKEND_PROXY_ONLY when public signing is invalid.
 */
@Component
public class ProfileAwareObjectDeliveryStrategy implements ObjectDeliveryStrategy {
    private static final Logger log = LoggerFactory.getLogger(ProfileAwareObjectDeliveryStrategy.class);
    private static final Set<String> BLOCKED_HOST_FRAGMENTS = Set.of(
        "actenora", "minio", "docker", "internal");

    private final ObjectDeliveryMode configuredMode;
    private final String publicEndpoint;
    private final String internalEndpoint;

    public ProfileAwareObjectDeliveryStrategy(
        @Value("${specai.storage.delivery-mode:BACKEND_PROXY_ONLY}") String deliveryMode,
        @Value("${specai.storage.public-endpoint:${MINIO_PUBLIC_ENDPOINT:}}") String publicEndpoint,
        @Value("${specai.storage.endpoint:${MINIO_ENDPOINT:}}") String internalEndpoint
    ) {
        this.configuredMode = parseMode(deliveryMode);
        this.publicEndpoint = publicEndpoint == null ? "" : publicEndpoint.trim();
        this.internalEndpoint = internalEndpoint == null ? "" : internalEndpoint.trim();
    }

    @Override
    public ObjectDeliveryResult createDelivery(ObjectDeliveryContext context) {
        ObjectDeliveryMode preferred = context.preferredMode() == null
            ? configuredMode : context.preferredMode();
        if (preferred == ObjectDeliveryMode.BACKEND_PROXY_ONLY
            || configuredMode == ObjectDeliveryMode.BACKEND_PROXY_ONLY) {
            return proxyOnly("DELIVERY_PROXY_ONLY");
        }
        if (!publicEndpoint.isBlank() && !isBlockedHost(publicEndpoint)) {
            URI uri = URI.create(trimSlash(publicEndpoint) + "/delivery/"
                + context.subjectType() + "/" + context.subjectId());
            return new ObjectDeliveryResult(
                preferred == ObjectDeliveryMode.REVERSE_PROXY
                    ? ObjectDeliveryMode.REVERSE_PROXY
                    : ObjectDeliveryMode.DIRECT_PUBLIC,
                uri,
                false,
                uri.getHost(),
                "DELIVERY_PUBLIC_OK");
        }
        log.warn("event=PRESIGN_PUBLIC_ENDPOINT_INVALID fallingBack=BACKEND_PROXY_ONLY");
        return proxyOnly("PRESIGN_PUBLIC_ENDPOINT_INVALID");
    }

    private ObjectDeliveryResult proxyOnly(String code) {
        return new ObjectDeliveryResult(
            ObjectDeliveryMode.BACKEND_PROXY_ONLY,
            null,
            true,
            null,
            code);
    }

    private static boolean isBlockedHost(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                return true;
            }
            if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                // Host-local MinIO may be valid for lab profiles, but not for browser
                // delivery from inside Docker. Treat as blocked for public delivery.
                return true;
            }
            for (String fragment : BLOCKED_HOST_FRAGMENTS) {
                if (host.contains(fragment)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static ObjectDeliveryMode parseMode(String value) {
        try {
            return ObjectDeliveryMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return ObjectDeliveryMode.BACKEND_PROXY_ONLY;
        }
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
