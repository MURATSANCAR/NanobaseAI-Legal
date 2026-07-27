package com.nanobase.specai.document.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {
    private final MinioClient client;
    private final String bucket;

    public MinioHealthIndicator(MinioClient client,
                                @Value("${specai.storage.original-bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        try {
            boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
            return exists
                ? Health.up().withDetail("bucket", bucket).build()
                : Health.down().withDetail("reason", "original bucket missing").build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }
}
