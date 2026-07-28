package com.nanobase.specai.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimitPolicyResolver {
    private final List<Policy> policies;

    public RateLimitPolicyResolver(
        @Value("${specai.rate-limits.upload.maximum:10}") int uploadMaximum,
        @Value("${specai.rate-limits.signed-url.maximum:30}") int signedUrlMaximum,
        @Value("${specai.rate-limits.processing.maximum:20}") int processingMaximum,
        @Value("${specai.rate-limits.sse.maximum:10}") int sseMaximum,
        @Value("${specai.rate-limits.search.maximum:120}") int searchMaximum,
        @Value("${specai.rate-limits.admin.maximum:30}") int adminMaximum
    ) {
        policies = List.of(
            new Policy("upload", "POST",
                Pattern.compile("^/api/v1/(tenders/[^/]+/documents|documents/[^/]+/versions)$"),
                uploadMaximum, true),
            new Policy("signed-url", "GET",
                Pattern.compile("^/api/v1/documents/[^/]+/download-url$"),
                signedUrlMaximum, false),
            new Policy("processing", "POST",
                Pattern.compile("^/api/v1/.+/(reprocess|requirement-extractions|"
                    + "knowledge-extractions|compliance-analyses|risk-analyses|reports)$"),
                processingMaximum, false),
            new Policy("sse", "GET",
                Pattern.compile("^/api/v1/.+/(events|processing-events)$"),
                sseMaximum, false),
            new Policy("search", "GET",
                Pattern.compile("^/api/v1/.+(search|query).*$"),
                searchMaximum, false),
            new Policy("admin", "*",
                Pattern.compile("^/api/v1/(operations|configuration)/.*$"),
                adminMaximum, false)
        );
        if (policies.stream().anyMatch(policy -> policy.maximum() <= 0)) {
            throw new IllegalArgumentException("Rate-limit policies must be positive");
        }
    }

    public Limit resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return policies.stream()
            .filter(policy -> "*".equals(policy.method()) || policy.method().equals(method))
            .filter(policy -> policy.path().matcher(path).matches())
            .findFirst()
            .map(policy -> new Limit(policy.name(), adjustedMaximum(policy, request)))
            .orElse(null);
    }

    private int adjustedMaximum(Policy policy, HttpServletRequest request) {
        if (!policy.fileSizeSensitive()) {
            return policy.maximum();
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > 50L * 1024 * 1024) {
            return Math.max(1, policy.maximum() / 4);
        }
        if (contentLength > 10L * 1024 * 1024) {
            return Math.max(1, policy.maximum() / 2);
        }
        return policy.maximum();
    }

    private record Policy(String name, String method, Pattern path, int maximum,
                          boolean fileSizeSensitive) {
    }

    public record Limit(String name, int maximum) {
    }
}
