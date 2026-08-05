package com.nanobase.specai.companyfit.application;

import com.nanobase.specai.companyfit.domain.CompanyFitModels.CapabilityKind;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyCapability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CompanyCapabilityExtractor {
    private static final Pattern ISO = Pattern.compile(
        "(?i)\\b(ISO\\s*/?\\s*IEC\\s*)?(27001|27002|9001|14001|45001)\\b");
    private static final Pattern TSE_CE = Pattern.compile(
        "(?i)\\b(TSE|TSEK|CE\\b|Ürün\\s*Sertifika)\\b");
    private static final Pattern BRAND = Pattern.compile(
        "(?i)\\b(yetkili\\s*(bayi|iş\\s*ortağ[iı]|partner|distributor|dağıtıcı)|"
            + "authorized\\s*(partner|dealer)|vmware\\s*(partner|authorized)|"
            + "dell\\s*partner|hpe\\s*partner|cisco\\s*partner)\\b");
    private static final Pattern EXPIRY = Pattern.compile(
        "(?i)(?:geçerlilik|expiry|valid\\s*(?:until|to)|son\\s*geçerlilik|bit[iı][sş]\\s*tarih[iı])"
            + "\\s*[:\\-]?\\s*(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern DIMM = Pattern.compile(
        "(?i)\\b(?:en\\s*az\\s*)?(\\d+)\\s*(?:adet\\s*)?(DIMM|bellek\\s*yuvas)");
    private static final Pattern CPU = Pattern.compile(
        "(?i)\\b(?:en\\s*az\\s*)?(\\d+)\\s*(?:adet\\s*)?(?:işlemci|cpu|xeon|çekirdek|core)");
    private static final Pattern PERSONNEL_YEARS = Pattern.compile(
        "(?i)\\b(\\d+)\\s*y[iı]l\\s*(?:deneyim|tecrübe)|deneyim[:\\s]+(\\d+)\\s*y[iı]l");
    private static final Pattern FINANCIAL = Pattern.compile(
        "(?i)\\b(teminat\\s*mektubu|banka\\s*referans|ciro|mali\\s*yeterlik|bilanço)\\b");
    private static final Pattern ADMIN = Pattern.compile(
        "(?i)\\b(ticaret\\s*sicil|oda\\s*kayıt|vergi\\s*levhas[iı]|sgk|faaliyet\\s*belgesi)\\b");

    public List<CompanyCapability> extract(String organizationId, String documentId, String text) {
        List<CompanyCapability> caps = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return caps;
        }
        String[] validity = validity(text);
        Matcher iso = ISO.matcher(text);
        while (iso.find()) {
            String std = iso.group(2);
            caps.add(cap(organizationId, documentId, CapabilityKind.COMPLIANCE_CERT,
                "iso_" + std, "ISO " + std, Map.of("standard", "ISO " + std),
                validity[0], validity[1], snippet(text, iso.start(), iso.end()), 0.9));
        }
        if (TSE_CE.matcher(text).find()) {
            caps.add(cap(organizationId, documentId, CapabilityKind.COMPLIANCE_CERT,
                "tse_or_ce", "TSE/CE belgesi", Map.of(),
                validity[0], validity[1], "TSE/CE signal", 0.8));
        }
        Matcher brand = BRAND.matcher(text);
        if (brand.find()) {
            caps.add(cap(organizationId, documentId, CapabilityKind.BRAND_AUTH,
                "brand_authorization", "Üretici / partner yetkisi", Map.of(),
                null, "UNKNOWN", snippet(text, brand.start(), brand.end()), 0.85));
        }
        Matcher dimm = DIMM.matcher(text);
        while (dimm.find()) {
            int n = Integer.parseInt(dimm.group(1));
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("measurement", "dimm");
            attrs.put("operator", ">=");
            attrs.put("value", n);
            caps.add(cap(organizationId, documentId, CapabilityKind.PRODUCT_SPEC,
                "min_dimm_slots", "DIMM slot ≥ " + n, attrs,
                null, "UNKNOWN", dimm.group(0), 0.88));
        }
        Matcher cpu = CPU.matcher(text);
        while (cpu.find()) {
            int n = Integer.parseInt(cpu.group(1));
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("measurement", "cpu_or_core");
            attrs.put("operator", ">=");
            attrs.put("value", n);
            caps.add(cap(organizationId, documentId, CapabilityKind.PRODUCT_SPEC,
                "min_cpu_or_cores", "CPU/core ≥ " + n, attrs,
                null, "UNKNOWN", cpu.group(0), 0.82));
        }
        Matcher years = PERSONNEL_YEARS.matcher(text);
        if (years.find()) {
            String raw = years.group(1) != null ? years.group(1) : years.group(2);
            int y = Integer.parseInt(raw);
            caps.add(cap(organizationId, documentId, CapabilityKind.PERSONNEL,
                "personnel_years", "Personel deneyim ≥ " + y + " yıl",
                Map.of("years", y), null, "UNKNOWN", years.group(0), 0.8));
        }
        Matcher fin = FINANCIAL.matcher(text);
        if (fin.find()) {
            caps.add(cap(organizationId, documentId, CapabilityKind.FINANCIAL,
                "financial_document", "Mali belge / teminat kapasitesi sinyali", Map.of(),
                null, "UNKNOWN", snippet(text, fin.start(), fin.end()), 0.7));
        }
        Matcher admin = ADMIN.matcher(text);
        if (admin.find()) {
            caps.add(cap(organizationId, documentId, CapabilityKind.ADMINISTRATIVE,
                "administrative_registration", "İdari kayıt belgesi", Map.of(),
                null, "UNKNOWN", snippet(text, admin.start(), admin.end()), 0.75));
        }
        return dedupe(caps);
    }

    private CompanyCapability cap(String org, String doc, CapabilityKind kind, String key,
                                  String label, Map<String, Object> attrs, String validTo,
                                  String status, String evidence, double confidence) {
        return new CompanyCapability(
            UUID.randomUUID().toString(), org, kind, key, label, attrs,
            null, validTo, status, doc, evidence, confidence);
    }

    private String[] validity(String text) {
        Matcher m = EXPIRY.matcher(text);
        if (!m.find()) {
            return new String[] {null, "UNKNOWN"};
        }
        return new String[] {m.group(1), "VALID"};
    }

    private String snippet(String text, int start, int end) {
        int a = Math.max(0, start - 20);
        int b = Math.min(text.length(), end + 40);
        return text.substring(a, b).replaceAll("\\s+", " ").trim();
    }

    private List<CompanyCapability> dedupe(List<CompanyCapability> caps) {
        Set<String> seen = new LinkedHashSet<>();
        List<CompanyCapability> out = new ArrayList<>();
        for (CompanyCapability c : caps) {
            Object value = c.attributes() == null ? "" : c.attributes().getOrDefault("value", "");
            String key = c.canonicalKey() + "|" + value;
            if (seen.add(key)) {
                out.add(c);
            }
        }
        return out;
    }
}
