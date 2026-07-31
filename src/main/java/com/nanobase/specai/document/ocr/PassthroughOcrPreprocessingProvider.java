package com.nanobase.specai.document.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Identity preprocessor chain entry — applies configured operation names from policy
 * without mutating bytes when no external engine is wired. Replaceable by real providers.
 */
@Component
@Order(100)
public class PassthroughOcrPreprocessingProvider implements OcrPreprocessingProvider {
    @Override
    public String providerCode() {
        return "PASSTHROUGH";
    }

    @Override
    public OcrPreprocessingResult process(OcrPreprocessingContext context) {
        List<String> ops = new ArrayList<>();
        Object requested = context.policyHints() == null
            ? null : context.policyHints().get("operations");
        if (requested instanceof List<?> list) {
            for (Object item : list) {
                String op = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
                if (!op.isBlank()) {
                    ops.add(op);
                }
            }
        }
        if (ops.isEmpty()) {
            ops = List.of("identity");
        }
        return new OcrPreprocessingResult(
            context.imageBytes(),
            context.mimeType(),
            List.copyOf(ops),
            Map.of("provider", providerCode()));
    }
}
