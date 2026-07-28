package com.nanobase.specai.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return current
        """, Long.class);
    private final StringRedisTemplate redis;
    private final CurrentTenant currentTenant;
    private final int windowSeconds;
    private final Map<String, LocalWindow> fallback = new ConcurrentHashMap<>();

    public RateLimitFilter(
        StringRedisTemplate redis,
        CurrentTenant currentTenant,
        @Value("${specai.security.rate-limit-window-seconds:60}") int windowSeconds) {
        this.redis = redis;
        this.currentTenant = currentTenant;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {
        Limit limit = limit(request);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }
        TenantPrincipal principal = currentTenant.require();
        String identity = principal.tenantId() + "|" + principal.subject() + "|"
            + remoteAddress(request) + "|" + limit.name();
        String key = "specai:rate-limit:" + sha256(identity);
        long count = increment(key);
        if (count <= limit.maximum()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit.maximum()));
            response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, limit.maximum() - count)));
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
            "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
                + "\"status\":429,\"detail\":\"Request rate limit exceeded\"}");
    }

    private long increment(String key) {
        try {
            Long count = redis.execute(INCREMENT, java.util.List.of(key),
                String.valueOf(windowSeconds));
            return count == null ? 1L : count;
        } catch (RuntimeException unavailable) {
            long window = Instant.now().getEpochSecond() / windowSeconds;
            LocalWindow value = fallback.compute(key, (ignored, current) ->
                current == null || current.window() != window
                    ? new LocalWindow(window, 1)
                    : new LocalWindow(window, current.count() + 1));
            return value.count();
        }
    }

    private Limit limit(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && path.matches("/api/v1/tenders/[^/]+/documents")) {
            return new Limit("upload", 10);
        }
        if ("POST".equals(method) && path.matches("/api/v1/documents/[^/]+/versions")) {
            return new Limit("upload-version", 10);
        }
        if ("POST".equals(method) && path.matches("/api/v1/documents/[^/]+/reprocess")) {
            return new Limit("reprocess", 5);
        }
        if ("GET".equals(method) && path.matches("/api/v1/documents/[^/]+/download-url")) {
            return new Limit("signed-url", 30);
        }
        if ("GET".equals(method) && (path.endsWith("/processing-events")
            || path.matches("/api/v1/processing-jobs/[^/]+/events"))) {
            return new Limit("sse", 10);
        }
        return null;
    }

    private String remoteAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
            ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Limit(String name, int maximum) {
    }

    private record LocalWindow(long window, long count) {
    }
}
