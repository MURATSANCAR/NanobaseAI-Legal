package com.nanobase.specai.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.document.application.DocumentService;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

class MinioObjectStorageTest {
    @Test
    void finalizationVerifiesSizeAndHashBeforeCopyAndDelete() throws Exception {
        byte[] content = "verified document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MinioClient client = mock(MinioClient.class);
        StatObjectResponse temporary = mock(StatObjectResponse.class);
        StatObjectResponse finalized = mock(StatObjectResponse.class);
        when(temporary.size()).thenReturn((long) content.length);
        when(finalized.size()).thenReturn((long) content.length);
        when(client.statObject(any())).thenReturn(temporary, finalized);
        when(client.getObject(any())).thenReturn(new GetObjectResponse(
            new Headers.Builder().build(), "specai-original", "us-east-1",
            "temp", new ByteArrayInputStream(content)));
        MinioObjectStorage storage = new MinioObjectStorage(client, "specai-original",
            "http://localhost:9000", "http://localhost:9000", "minioadmin", "minioadmin");

        storage.finalizeObject("specai-temp/org/upload/spec.pdf",
            "specai-original/org/project/document/version/spec.pdf",
            content.length, DocumentService.sha256(new ByteArrayInputStream(content)));

        verify(client).composeObject(any());
        verify(client).removeObject(any());
    }

    @Test
    void hashMismatchNeverFinalizesObject() throws Exception {
        byte[] content = "tampered".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MinioClient client = mock(MinioClient.class);
        StatObjectResponse temporary = mock(StatObjectResponse.class);
        when(temporary.size()).thenReturn((long) content.length);
        when(client.statObject(any())).thenReturn(temporary);
        when(client.getObject(any())).thenReturn(new GetObjectResponse(
            new Headers.Builder().build(), "specai-original", "us-east-1",
            "temp", new ByteArrayInputStream(content)));
        MinioObjectStorage storage = new MinioObjectStorage(client, "specai-original",
            "http://localhost:9000", "http://localhost:9000", "minioadmin", "minioadmin");

        assertThatThrownBy(() -> storage.finalizeObject("temporary", "final",
            content.length, "0".repeat(64)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hash verification");
        verify(client, never()).composeObject(any());
    }
}
