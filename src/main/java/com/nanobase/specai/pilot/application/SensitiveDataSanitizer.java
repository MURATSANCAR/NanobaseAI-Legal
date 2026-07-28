package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataSanitizer {
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
        "documenttext", "documentcontent", "evidencetext", "prompt", "promptcontent",
        "rawmodelinput", "modelinput", "rawmodeloutput", "modeloutput", "signedurl",
        "token", "accesstoken", "refreshtoken", "secret", "password", "apikey",
        "authorization", "personaldata", "tradesecret", "text", "content", "body");
    private static final Pattern EMAIL = Pattern.compile(
        "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final int MAX_STRING_LENGTH = 4_000;
    private final ObjectMapper mapper;

    public SensitiveDataSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SanitizedPayload sanitize(JsonNode input) {
        List<String> removedPaths = new ArrayList<>();
        JsonNode sanitized = sanitizeNode(input == null ? mapper.createObjectNode() : input,
            "$", removedPaths, 0);
        return new SanitizedPayload(sanitized, List.copyOf(removedPaths), sha256(sanitized));
    }

    private JsonNode sanitizeNode(JsonNode node, String path, List<String> removed, int depth) {
        if (depth > 32) {
            removed.add(path + ":depth-limit");
            return mapper.getNodeFactory().textNode("[REDACTED_DEPTH_LIMIT]");
        }
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldPath = path + "." + field.getKey();
                if (forbidden(field.getKey())) {
                    removed.add(fieldPath);
                    continue;
                }
                result.set(field.getKey(),
                    sanitizeNode(field.getValue(), fieldPath, removed, depth + 1));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (int index = 0; index < node.size(); index++) {
                result.add(sanitizeNode(node.get(index), path + "[" + index + "]",
                    removed, depth + 1));
            }
            return result;
        }
        if (node.isTextual()) {
            String value = BEARER.matcher(node.textValue()).replaceAll("[REDACTED_TOKEN]");
            value = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
            if (value.length() > MAX_STRING_LENGTH) {
                removed.add(path + ":truncated");
                value = value.substring(0, MAX_STRING_LENGTH);
            }
            return mapper.getNodeFactory().textNode(value);
        }
        return node.deepCopy();
    }

    private boolean forbidden(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return FORBIDDEN_KEYS.contains(normalized);
    }

    public String sha256(JsonNode node) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(node.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record SanitizedPayload(JsonNode value, List<String> removedPaths, String contentHash) {
    }
}
