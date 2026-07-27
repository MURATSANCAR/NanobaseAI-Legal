package com.nanobase.specai.document.application;

import java.text.Normalizer;

public final class FileNameSanitizer {
    private static final int MAX_LENGTH = 180;

    private FileNameSanitizer() {
    }

    public static String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String pathSafe = original.replace('\\', '/');
        String baseName = pathSafe.substring(pathSafe.lastIndexOf('/') + 1);
        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFKC)
            .replaceAll("[\\p{Cntrl}]", "_")
            .replaceAll("[<>:\"/\\\\|?*]", "_")
            .replaceAll("\\s+", " ")
            .trim()
            .replaceAll("^\\.+", "");
        if (normalized.isBlank()) {
            return "document";
        }
        return normalized.length() <= MAX_LENGTH
            ? normalized
            : preserveExtension(normalized);
    }

    private static String preserveExtension(String value) {
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || value.length() - dot > 15) {
            return value.substring(0, MAX_LENGTH);
        }
        String extension = value.substring(dot);
        return value.substring(0, MAX_LENGTH - extension.length()) + extension;
    }
}
