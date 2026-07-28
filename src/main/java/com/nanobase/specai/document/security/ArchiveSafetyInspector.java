package com.nanobase.specai.document.security;

import com.nanobase.specai.document.application.InvalidDocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ArchiveSafetyInspector {
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
        ".zip", ".jar", ".7z", ".rar", ".tar", ".gz", ".bz2", ".xz");
    private static final byte[] PDF_ENCRYPT = "/Encrypt".getBytes(StandardCharsets.US_ASCII);
    private final int maximumEntries;
    private final long maximumUncompressedBytes;
    private final double maximumCompressionRatio;
    private final int maximumPdfPages;

    public ArchiveSafetyInspector(
        @Value("${specai.file-security.archive.maximum-entries:10000}") int maximumEntries,
        @Value("${specai.file-security.archive.maximum-uncompressed-bytes:536870912}")
        long maximumUncompressedBytes,
        @Value("${specai.file-security.archive.maximum-compression-ratio:100}") double ratio,
        @Value("${specai.file-security.pdf.maximum-pages:1000}") int maximumPdfPages
    ) {
        this.maximumEntries = maximumEntries;
        this.maximumUncompressedBytes = maximumUncompressedBytes;
        this.maximumCompressionRatio = ratio;
        this.maximumPdfPages = maximumPdfPages;
    }

    public Map<String, Object> inspect(MultipartFile file, String mimeType) {
        try {
            if ("application/pdf".equals(mimeType)) {
                return inspectPdf(file);
            }
            if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(mimeType)) {
                return inspectDocx(file);
            }
            throw new InvalidDocumentException("Unsupported content type");
        } catch (ZipException exception) {
            throw new InvalidDocumentException(
                "Encrypted or malformed archive content is not accepted");
        } catch (IOException exception) {
            throw new InvalidDocumentException("File security inspection failed");
        }
    }

    private Map<String, Object> inspectPdf(MultipartFile file) throws IOException {
        int pageMarkers = 0;
        boolean encrypted = false;
        byte[] overlap = new byte[PDF_ENCRYPT.length - 1];
        int overlapSize = 0;
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) {
                    continue;
                }
                ByteArrayOutputStream window = new ByteArrayOutputStream(overlapSize + read);
                window.write(overlap, 0, overlapSize);
                window.write(buffer, 0, read);
                byte[] bytes = window.toByteArray();
                encrypted |= contains(bytes, PDF_ENCRYPT);
                pageMarkers += count(bytes, "/Type /Page".getBytes(StandardCharsets.US_ASCII));
                overlapSize = Math.min(overlap.length, bytes.length);
                System.arraycopy(bytes, bytes.length - overlapSize, overlap, 0, overlapSize);
            }
        }
        if (encrypted) {
            throw new InvalidDocumentException(
                "Encrypted PDF requires manual security review before processing");
        }
        if (pageMarkers > maximumPdfPages) {
            throw new InvalidDocumentException("PDF exceeds the configured page safety limit");
        }
        return Map.of("encrypted", false, "estimatedPageCount", pageMarkers,
            "maximumPageCount", maximumPdfPages);
    }

    private Map<String, Object> inspectDocx(MultipartFile file) throws IOException {
        int entries = 0;
        long expandedBytes = 0;
        long declaredCompressedBytes = 0;
        int nestedArchives = 0;
        try (ZipInputStream archive = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = archive.getNextEntry()) != null) {
                entries++;
                if (entries > maximumEntries) {
                    throw new InvalidDocumentException("Archive contains too many entries");
                }
                String normalized = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (normalized.startsWith("/") || normalized.contains("../")) {
                    throw new InvalidDocumentException("Archive contains an unsafe path");
                }
                if (ARCHIVE_EXTENSIONS.stream().anyMatch(normalized::endsWith)) {
                    nestedArchives++;
                    throw new InvalidDocumentException("Nested archives are not accepted");
                }
                declaredCompressedBytes += Math.max(0, entry.getCompressedSize());
                for (int read; (read = archive.read(buffer)) >= 0;) {
                    expandedBytes += read;
                    if (expandedBytes > maximumUncompressedBytes) {
                        throw new InvalidDocumentException(
                            "Archive expansion exceeds the safety limit");
                    }
                }
                archive.closeEntry();
            }
        }
        long denominator = declaredCompressedBytes > 0
            ? declaredCompressedBytes : Math.max(1, file.getSize());
        double ratio = (double) expandedBytes / denominator;
        if (ratio > maximumCompressionRatio) {
            throw new InvalidDocumentException("Archive compression ratio exceeds the safety limit");
        }
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("entries", entries);
        signals.put("uncompressedBytes", expandedBytes);
        signals.put("compressionRatio", ratio);
        signals.put("nestedArchives", nestedArchives);
        return Map.copyOf(signals);
    }

    private boolean contains(byte[] source, byte[] pattern) {
        return count(source, pattern) > 0;
    }

    private int count(byte[] source, byte[] pattern) {
        int matches = 0;
        for (int index = 0; index <= source.length - pattern.length; index++) {
            boolean match = true;
            for (int offset = 0; offset < pattern.length; offset++) {
                if (source[index + offset] != pattern[offset]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matches++;
            }
        }
        return matches;
    }
}
