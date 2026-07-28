package com.nanobase.specai.document.security;

import com.nanobase.specai.document.security.FileSecurityScanner.ScanResult.Status;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClamAvFileSecurityScanner implements FileSecurityScanner {
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAXIMUM_RESPONSE_BYTES = 4096;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final boolean enabled;
    private final boolean failClosed;

    public ClamAvFileSecurityScanner(
        @Value("${specai.file-security.clamav.host:localhost}") String host,
        @Value("${specai.file-security.clamav.port:3310}") int port,
        @Value("${specai.file-security.clamav.connect-timeout-ms:2000}") int connectTimeoutMs,
        @Value("${specai.file-security.clamav.read-timeout-ms:120000}") int readTimeoutMs,
        @Value("${specai.file-security.clamav.enabled:true}") boolean enabled,
        @Value("${specai.file-security.fail-closed:true}") boolean failClosed
    ) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.enabled = enabled;
        this.failClosed = failClosed;
    }

    @Override
    public ScanResult scan(InputStream content) {
        if (!enabled) {
            Status status = failClosed ? Status.SECURITY_SCAN_FAILED : Status.MANUAL_SECURITY_REVIEW;
            return new ScanResult(status, "CLAMAV", null,
                Map.of("code", "SCANNER_DISABLED"));
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK_SIZE];
            try (BufferedInputStream input = new BufferedInputStream(content)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.writeInt(read);
                    output.write(buffer, 0, read);
                }
            }
            output.writeInt(0);
            output.flush();
            String response = readResponse(socket.getInputStream());
            if (response.endsWith(" OK")) {
                return new ScanResult(Status.SAFE, "CLAMAV", null,
                    Map.of("result", "CLEAN"));
            }
            if (response.contains(" FOUND")) {
                String signature = response.substring(
                    response.indexOf(':') + 1, response.lastIndexOf(" FOUND")).trim();
                return new ScanResult(Status.MALICIOUS, "CLAMAV", null,
                    Map.of("result", "MALWARE_FOUND", "signature", signature));
            }
            return new ScanResult(Status.SECURITY_SCAN_FAILED, "CLAMAV", null,
                Map.of("code", "UNEXPECTED_SCANNER_RESPONSE"));
        } catch (IOException exception) {
            return new ScanResult(Status.SECURITY_SCAN_FAILED, "CLAMAV", null,
                Map.of("code", "SCANNER_UNAVAILABLE",
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        for (int value; (value = input.read()) >= 0 && value != 0;) {
            if (response.size() >= MAXIMUM_RESPONSE_BYTES) {
                throw new IOException("ClamAV response exceeds the safety limit");
            }
            response.write(value);
        }
        return response.toString(StandardCharsets.US_ASCII).trim();
    }
}
