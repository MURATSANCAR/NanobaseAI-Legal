package com.nanobase.specai.document.infrastructure;

import com.nanobase.specai.document.application.ObjectStorage;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(MinioClient client,
                              @Value("${specai.storage.original-bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, InputStream content, long size, String mediaType) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                .stream(content, size, -1).contentType(mediaType).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Object storage write failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Object storage cleanup failed", exception);
        }
    }

    @Override
    public URI signedDownloadUrl(String objectKey, Duration validity) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(objectKey)
                .expiry(Math.toIntExact(validity.toSeconds())).build());
            return URI.create(url);
        } catch (Exception exception) {
            throw new IllegalStateException("Signed URL creation failed", exception);
        }
    }
}
