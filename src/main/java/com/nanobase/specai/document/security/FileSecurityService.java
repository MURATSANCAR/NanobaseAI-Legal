package com.nanobase.specai.document.security;

import com.nanobase.specai.document.application.InvalidDocumentException;
import com.nanobase.specai.document.security.FileSecurityScanner.ScanResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileSecurityService {
    private final ArchiveSafetyInspector archiveInspector;
    private final FileSecurityScanner scanner;
    private final FileSecurityAssessmentRecorder recorder;

    public FileSecurityService(ArchiveSafetyInspector archiveInspector,
                               FileSecurityScanner scanner,
                               FileSecurityAssessmentRecorder recorder) {
        this.archiveInspector = archiveInspector;
        this.scanner = scanner;
        this.recorder = recorder;
    }

    public void requireSafe(UUID organizationId, UUID projectId, String sha256,
                            String mimeType, MultipartFile file, UUID correlationId) {
        Map<String, Object> preflight;
        ScanResult result;
        try {
            preflight = archiveInspector.inspect(file, mimeType);
            try (InputStream content = file.getInputStream()) {
                result = scanner.scan(content);
            }
        } catch (IOException exception) {
            result = new ScanResult(ScanResult.Status.SECURITY_SCAN_FAILED,
                "CLAMAV", null, Map.of("code", "CONTENT_READ_FAILED"));
            preflight = Map.of();
        } catch (InvalidDocumentException rejected) {
            result = new ScanResult(ScanResult.Status.MANUAL_SECURITY_REVIEW,
                "PREFLIGHT", null, Map.of("code", "PREFLIGHT_REJECTED"));
            recorder.record(organizationId, projectId, sha256, result, Map.of(), correlationId);
            throw rejected;
        }
        recorder.record(organizationId, projectId, sha256, result, preflight, correlationId);
        switch (result.status()) {
            case SAFE -> {
                return;
            }
            case MALICIOUS -> throw new InvalidDocumentException(
                "File was rejected by malware scanning");
            case MANUAL_SECURITY_REVIEW -> throw new InvalidDocumentException(
                "File requires manual security review");
            case SECURITY_SCAN_FAILED -> throw new InvalidDocumentException(
                "File security scanning is temporarily unavailable");
        }
    }
}
