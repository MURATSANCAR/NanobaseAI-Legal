package com.nanobase.specai.document.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ClamAvFileSecurityScannerTest {
    @Test
    void streamsContentAndAcceptsOnlyExplicitCleanResponse() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responder = executor.submit(() -> {
                try (var socket = server.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    byte[] command = input.readNBytes("zINSTREAM\0".length());
                    assertThat(new String(command, StandardCharsets.US_ASCII))
                        .isEqualTo("zINSTREAM\0");
                    int length = input.readInt();
                    assertThat(input.readNBytes(length)).isEqualTo("safe".getBytes());
                    assertThat(input.readInt()).isZero();
                    socket.getOutputStream().write("stream: OK\0"
                        .getBytes(StandardCharsets.US_ASCII));
                }
                return true;
            });
            var scanner = new ClamAvFileSecurityScanner(
                "127.0.0.1", server.getLocalPort(), 1000, 1000, true, true);

            var result = scanner.scan(new java.io.ByteArrayInputStream("safe".getBytes()));

            assertThat(result.status())
                .isEqualTo(FileSecurityScanner.ScanResult.Status.SAFE);
            assertThat(responder.get()).isTrue();
        }
    }

    @Test
    void failsClosedWhenScannerIsUnavailable() {
        var scanner = new ClamAvFileSecurityScanner(
            "127.0.0.1", 1, 50, 50, true, true);

        var result = scanner.scan(new java.io.ByteArrayInputStream("content".getBytes()));

        assertThat(result.status())
            .isEqualTo(FileSecurityScanner.ScanResult.Status.SECURITY_SCAN_FAILED);
    }
}
