package com.nanobase.specai.companyfit.application;

import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyCapability;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyFitReport;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.FitEvidence;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.FitStatus;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.OverallFit;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.RequirementFit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CompanyFitEngine {
    private static final Set<String> MUST = Set.of("MUST", "REQUIRED", "MANDATORY", "ZORUNLU");

    private static final Map<String, List<String>> CATEGORY_KEYS = Map.of(
        "COMPLIANCE", List.of("iso_27001", "iso_9001", "tse_or_ce", "iso_14001", "iso_45001"),
        "SECURITY", List.of("iso_27001", "security_ctrl"),
        "DOCUMENT", List.of("administrative_registration", "financial_document", "tse_or_ce"),
        "PERSONNEL", List.of("personnel_years"),
        "FINANCIAL", List.of("financial_document"),
        "TECHNICAL", List.of("min_dimm_slots", "min_cpu_or_cores", "brand_authorization", "product_spec"),
        "OPERATIONAL", List.of("brand_authorization", "personnel_years"),
        "CERTIFICATION", List.of("iso_27001", "iso_9001", "tse_or_ce")
    );

    private static final List<Map.Entry<Pattern, String>> TEXT_KEY_RULES = List.of(
        Map.entry(Pattern.compile("(?i)iso\\s*27001"), "iso_27001"),
        Map.entry(Pattern.compile("(?i)iso\\s*9001"), "iso_9001"),
        Map.entry(Pattern.compile("(?i)\\b(tse|ce)\\b"), "tse_or_ce"),
        Map.entry(Pattern.compile("(?i)vmware|yetkili|partner"), "brand_authorization"),
        Map.entry(Pattern.compile("(?i)dimm|bellek\\s*yuvas"), "min_dimm_slots"),
        Map.entry(Pattern.compile("(?i)çekirdek|işlemci|xeon|cpu"), "min_cpu_or_cores"),
        Map.entry(Pattern.compile("(?i)deneyim|personel|uzman"), "personnel_years"),
        Map.entry(Pattern.compile("(?i)teminat|mali\\s*yeter"), "financial_document"),
        Map.entry(Pattern.compile("(?i)ticaret\\s*sicil|vergi\\s*levha|oda"), "administrative_registration")
    );

    public RequirementFit matchRequirement(Map<String, Object> req, List<CompanyCapability> caps) {
        String rid = str(req.get("requirementId"), str(req.get("id"), ""));
        String preview = str(req.get("text"), str(req.get("title"), ""));
        if (preview.length() > 180) {
            preview = preview.substring(0, 180);
        }
        List<String> wanted = wantedKeys(req);
        Map<String, List<CompanyCapability>> idx = indexByKey(caps);
        if (wanted.isEmpty()) {
            return new RequirementFit(rid, str(req.get("category"), null),
                priorityOf(req), preview, FitStatus.AMBIGUOUS, 0.0,
                List.of("no_capability_mapping"), List.of(),
                "Manuel eşleştirme veya canonical key ekle");
        }

        List<FitEvidence> evidence = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        double best = 0.0;
        boolean partial = false;

        for (String key : wanted) {
            for (CompanyCapability cap : idx.getOrDefault(key, List.of())) {
                if ("EXPIRED".equalsIgnoreCase(cap.validityStatus())) {
                    reasons.add("expired:" + key);
                    partial = true;
                    continue;
                }
                Boolean num = numericMet(req, cap);
                if (Boolean.FALSE.equals(num)) {
                    reasons.add("threshold_not_met:" + key);
                    partial = true;
                    evidence.add(new FitEvidence(cap.capabilityId(), cap.sourceDocumentId(),
                        cap.evidenceSnippet(), "threshold_not_met"));
                    continue;
                }
                double score = Math.min(1.0, 0.6 + 0.4 * cap.confidence());
                if (Boolean.TRUE.equals(num)) {
                    score = Math.min(1.0, score + 0.1);
                }
                best = Math.max(best, score);
                evidence.add(new FitEvidence(cap.capabilityId(), cap.sourceDocumentId(),
                    cap.evidenceSnippet(), null));
                reasons.add("matched:" + key);
            }
        }

        FitStatus status;
        String action;
        if (best >= 0.6 && !evidence.isEmpty()) {
            status = FitStatus.MET;
            action = null;
        } else if (partial || (best > 0 && best < 0.6)) {
            status = FitStatus.PARTIAL;
            action = "Eksik eşik / güncel belge yükle";
        } else {
            status = FitStatus.MISSING;
            action = "Eksik yetenek: " + String.join(", ", wanted.subList(0, Math.min(3, wanted.size())));
            reasons.add("no_matching_capability");
        }
        return new RequirementFit(rid, str(req.get("category"), null), priorityOf(req),
            preview, status, best, reasons, evidence, action);
    }

    public CompanyFitReport buildReport(String organizationId, String tenderDocumentId,
                                        List<Map<String, Object>> requirements,
                                        List<CompanyCapability> capabilities) {
        List<RequirementFit> rows = new ArrayList<>();
        for (Map<String, Object> req : requirements) {
            rows.add(matchRequirement(req, capabilities));
        }
        List<RequirementFit> mustRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (isMust(requirements.get(i))) {
                mustRows.add(rows.get(i));
            }
        }
        if (mustRows.isEmpty()) {
            mustRows = rows;
        }
        int mustTotal = mustRows.size();
        int mustMet = (int) mustRows.stream().filter(r -> r.status() == FitStatus.MET).count();
        double coverage = mustTotal == 0 ? 0.0 : (double) mustMet / mustTotal;

        List<String> missingCritical = new ArrayList<>();
        for (RequirementFit r : mustRows) {
            if ((r.status() == FitStatus.MISSING || r.status() == FitStatus.AMBIGUOUS)
                && Set.of("TECHNICAL", "COMPLIANCE", "SECURITY", "DOCUMENT", "FINANCIAL",
                    "CERTIFICATION").contains(upper(r.category()))) {
                missingCritical.add(r.requirementId());
            }
        }

        OverallFit overall;
        if (capabilities == null || capabilities.isEmpty()) {
            overall = OverallFit.INSUFFICIENT_DATA;
        } else if (coverage >= 0.85 && missingCritical.isEmpty()) {
            overall = OverallFit.FIT;
        } else if (coverage >= 0.55) {
            overall = OverallFit.CONDITIONAL;
        } else {
            overall = OverallFit.NOT_FIT;
        }

        return new CompanyFitReport(
            organizationId, tenderDocumentId, overall,
            Math.round(coverage * 10000.0) / 10000.0,
            mustMet, mustTotal, missingCritical, rows,
            Instant.now().toString(), "company-fit-v1");
    }

    private List<String> wantedKeys(Map<String, Object> req) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String cat = upper(str(req.get("category"), str(req.get("requirementType"), "")));
        keys.addAll(CATEGORY_KEYS.getOrDefault(cat, List.of()));
        String text = str(req.get("title"), "") + "\n" + str(req.get("text"), "");
        for (var rule : TEXT_KEY_RULES) {
            if (rule.getKey().matcher(text).find()) {
                keys.add(rule.getValue());
            }
        }
        Object measurement = req.get("measurement");
        if (measurement != null && req.get("acceptanceThreshold") != null) {
            String m = measurement.toString().toLowerCase(Locale.ROOT);
            if (m.contains("dimm")) {
                keys.add("min_dimm_slots");
            }
            if (m.contains("çekirdek") || m.contains("core") || m.contains("cpu") || m.contains("işlemci")) {
                keys.add("min_cpu_or_cores");
            }
        }
        return new ArrayList<>(keys);
    }

    private Map<String, List<CompanyCapability>> indexByKey(List<CompanyCapability> caps) {
        Map<String, List<CompanyCapability>> idx = new LinkedHashMap<>();
        for (CompanyCapability c : caps) {
            idx.computeIfAbsent(c.canonicalKey(), k -> new ArrayList<>()).add(c);
        }
        return idx;
    }

    private Boolean numericMet(Map<String, Object> req, CompanyCapability cap) {
        Object threshold = req.get("acceptanceThreshold");
        if (threshold == null) {
            return null;
        }
        double need;
        try {
            need = Double.parseDouble(threshold.toString().replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
        Object val = cap.attributes() == null ? null : cap.attributes().get("value");
        if (val == null) {
            return null;
        }
        double have;
        try {
            have = Double.parseDouble(val.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
        String op = str(req.get("operator"), ">=");
        return switch (op) {
            case "<=", "=<" -> have <= need;
            case "==", "=" -> have == need;
            default -> have >= need;
        };
    }

    private boolean isMust(Map<String, Object> req) {
        String p = upper(priorityOf(req));
        return MUST.contains(p);
    }

    private String priorityOf(Map<String, Object> req) {
        String p = str(req.get("priority"), str(req.get("obligationLevel"), ""));
        if ("MANDATORY".equalsIgnoreCase(p)) {
            return "MUST";
        }
        return p;
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
