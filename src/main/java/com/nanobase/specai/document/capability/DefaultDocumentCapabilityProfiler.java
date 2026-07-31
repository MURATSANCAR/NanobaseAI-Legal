package com.nanobase.specai.document.capability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Heuristic capability profiler. Content-mode codes are open strings;
 * routing decisions stay in {@link DocumentProcessingRouter}.
 */
@Component
public class DefaultDocumentCapabilityProfiler {

    public DocumentCapabilityProfile profile(
        UUID organizationId,
        UUID documentVersionId,
        String mimeType,
        String fileExtension,
        int pageCount,
        Double digitalTextRatio,
        boolean scanLikely,
        int tableCount,
        int imageHintCount,
        int textCharCount,
        Map<String, Object> languageHints
    ) {
        String format = resolveFormat(mimeType, fileExtension);
        double textRatio = digitalTextRatio == null ? 0.5d : digitalTextRatio;
        String contentMode = resolveContentMode(format, textRatio, scanLikely, tableCount, pageCount);
        String layoutComplexity = resolveLayoutComplexity(tableCount, pageCount, textCharCount);
        String ocrNeed = resolveOcrNeed(format, contentMode, textRatio, scanLikely);
        BigDecimal tableDensity = density(tableCount, Math.max(1, pageCount));
        BigDecimal imageDensity = density(imageHintCount, Math.max(1, pageCount));
        BigDecimal textDensity = BigDecimal.valueOf(Math.min(1.0d, textRatio))
            .setScale(4, RoundingMode.HALF_UP);
        int tokens = Math.max(0, textCharCount / 4);
        return new DocumentCapabilityProfile(
            UUID.randomUUID(),
            organizationId,
            documentVersionId,
            format,
            contentMode,
            layoutComplexity,
            ocrNeed,
            tableDensity,
            imageDensity,
            textDensity,
            BigDecimal.valueOf(contentMode.contains("DOCX") ? 0.75d : 0.55d)
                .setScale(4, RoundingMode.HALF_UP),
            languageHints == null ? Map.of() : Map.copyOf(languageHints),
            Math.max(0, pageCount),
            tokens,
            recommendedParser(format, contentMode),
            recommendedOcr(ocrNeed),
            "TEXT_HIERARCHY_CHAIN",
            "BALANCED",
            Instant.now()
        );
    }

    private static String resolveFormat(String mimeType, String extension) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (mime.contains("wordprocessingml") || ".docx".equals(ext)) {
            return "DOCX";
        }
        if (mime.contains("pdf") || ".pdf".equals(ext)) {
            return "PDF";
        }
        return "UNKNOWN";
    }

    private static String resolveContentMode(
        String format, double textRatio, boolean scanLikely, int tableCount, int pageCount
    ) {
        if ("DOCX".equals(format)) {
            return tableCount > Math.max(2, pageCount / 3) ? "TABLE_DOMINANT" : "DOCX_STRUCTURED";
        }
        if (scanLikely || textRatio < 0.10d) {
            return "SCANNED_IMAGE";
        }
        if (textRatio < 0.35d) {
            return "MIXED_TEXT_IMAGE";
        }
        if (tableCount > Math.max(3, pageCount / 2)) {
            return "TABLE_DOMINANT";
        }
        return "NATIVE_TEXT";
    }

    private static String resolveLayoutComplexity(int tableCount, int pageCount, int textChars) {
        if (tableCount > 8 || pageCount > 100 || textChars > 400_000) {
            return "HIGH";
        }
        if (tableCount > 2 || pageCount > 30) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String resolveOcrNeed(
        String format, String contentMode, double textRatio, boolean scanLikely
    ) {
        if ("DOCX".equals(format)) {
            return "NONE";
        }
        if ("SCANNED_IMAGE".equals(contentMode) || scanLikely || textRatio < 0.10d) {
            return "REQUIRED";
        }
        if ("MIXED_TEXT_IMAGE".equals(contentMode) || textRatio < 0.35d) {
            return "RECOMMENDED";
        }
        return "OPTIONAL";
    }

    private static String recommendedParser(String format, String contentMode) {
        if ("DOCX".equals(format)) {
            return "DOCLING_DOCX";
        }
        if ("SCANNED_IMAGE".equals(contentMode)) {
            return "DOCLING_OCR";
        }
        return "DOCLING_AUTO";
    }

    private static String recommendedOcr(String ocrNeed) {
        return switch (ocrNeed) {
            case "REQUIRED" -> "PRIMARY_OCR";
            case "RECOMMENDED" -> "AUTO_OCR";
            default -> "DISABLED";
        };
    }

    private static BigDecimal density(int count, int pages) {
        return BigDecimal.valueOf((double) count / (double) pages)
            .setScale(4, RoundingMode.HALF_UP);
    }
}
