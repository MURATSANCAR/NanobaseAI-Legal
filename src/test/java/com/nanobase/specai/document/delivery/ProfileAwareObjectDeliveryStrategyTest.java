package com.nanobase.specai.document.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileAwareObjectDeliveryStrategyTest {

    @Test
    void fallsBackToProxyWhenPublicEndpointIsDockerInternal() {
        ProfileAwareObjectDeliveryStrategy strategy = new ProfileAwareObjectDeliveryStrategy(
            "DIRECT_PUBLIC",
            "http://actenora-prodlike-minio:9000",
            "http://minio:9000");
        ObjectDeliveryResult result = strategy.createDelivery(new ObjectDeliveryContext(
            UUID.randomUUID(), UUID.randomUUID(), "DOCUMENT", "k", "application/pdf",
            Instant.now().plusSeconds(60), ObjectDeliveryMode.DIRECT_PUBLIC));
        assertThat(result.mode()).isEqualTo(ObjectDeliveryMode.BACKEND_PROXY_ONLY);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.telemetryCode()).isEqualTo("PRESIGN_PUBLIC_ENDPOINT_INVALID");
    }

    @Test
    void proxyOnlyProfileNeverEmitsInternalHost() {
        ProfileAwareObjectDeliveryStrategy strategy = new ProfileAwareObjectDeliveryStrategy(
            "BACKEND_PROXY_ONLY",
            "https://files.example.com",
            "http://minio:9000");
        ObjectDeliveryResult result = strategy.createDelivery(new ObjectDeliveryContext(
            UUID.randomUUID(), UUID.randomUUID(), "REPORT", "k", "application/pdf",
            Instant.now().plusSeconds(60), null));
        assertThat(result.mode()).isEqualTo(ObjectDeliveryMode.BACKEND_PROXY_ONLY);
        assertThat(result.url()).isNull();
    }
}
