package com.nanobase.specai.document.security;

import java.io.InputStream;
import java.util.Map;

public interface FileSecurityScanner {
    ScanResult scan(InputStream content);

    record ScanResult(Status status, String scanner, String scannerVersion,
                      Map<String, Object> signals) {
        public enum Status {
            SAFE,
            MALICIOUS,
            SECURITY_SCAN_FAILED,
            MANUAL_SECURITY_REVIEW
        }
    }
}
