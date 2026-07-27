package com.nanobase.specai.document.application;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorage {
    void put(String objectKey, InputStream content, long size, String mediaType);
    void delete(String objectKey);
    URI signedDownloadUrl(String objectKey, Duration validity);
}
