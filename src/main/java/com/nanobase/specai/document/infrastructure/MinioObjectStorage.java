package com.nanobase.specai.document.infrastructure;

import com.nanobase.specai.document.application.ObjectStorage;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;
import io.minio.messages.Item;
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

    @Override
    public void finalizeObject(String temporaryKey, String finalKey,
                               long expectedSize, String expectedSha256) {
        try {
            StatObjectResponse temporary = client.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(temporaryKey).build());
            if (temporary.size() != expectedSize) {
                throw new IllegalStateException("Temporary object size verification failed");
            }
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream source = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(temporaryKey).build());
                 DigestInputStream digest = new DigestInputStream(source, sha256)) {
                digest.transferTo(java.io.OutputStream.nullOutputStream());
            }
            String actualHash = HexFormat.of().formatHex(sha256.digest());
            if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                throw new IllegalStateException("Temporary object hash verification failed");
            }
            client.composeObject(ComposeObjectArgs.builder().bucket(bucket).object(finalKey)
                .sources(List.of(ComposeSource.builder().bucket(bucket)
                    .object(temporaryKey).build()))
                .build());
            StatObjectResponse finalized = client.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(finalKey).build());
            if (finalized.size() != expectedSize) {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket)
                    .object(finalKey).build());
                throw new IllegalStateException("Final object size verification failed");
            }
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket)
                .object(temporaryKey).build());
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Object storage finalization failed", exception);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try {
            Iterable<io.minio.Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
            return StreamSupport.stream(results.spliterator(), false).map(result -> {
                try {
                    Item item = result.get();
                    return new StoredObject(item.objectName(), item.size(),
                        item.lastModified().toInstant());
                } catch (Exception exception) {
                    throw new IllegalStateException("Object listing failed", exception);
                }
            }).toList();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Object listing failed", exception);
        }
    }
}
