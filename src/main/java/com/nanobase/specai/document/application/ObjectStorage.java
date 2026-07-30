package com.nanobase.specai.document.application;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface ObjectStorage {
    void put(String objectKey, InputStream content, long size, String mediaType);
    void delete(String objectKey);
    URI signedDownloadUrl(String objectKey, Duration validity);

    default InputStream open(String objectKey) {
        throw new UnsupportedOperationException("Object streaming is not supported");
    }

    default StoredObjectStat stat(String objectKey) {
        throw new UnsupportedOperationException("Object stat is not supported");
    }

    default void finalizeObject(String temporaryKey, String finalKey,
                                long expectedSize, String expectedSha256) {
        throw new UnsupportedOperationException("Object finalization is not supported");
    }

    default List<StoredObject> list(String prefix) {
        return List.of();
    }

    record StoredObject(String objectKey, long size, Instant lastModified) {
    }

    record StoredObjectStat(String objectKey, long size, String contentType) {
    }
}
