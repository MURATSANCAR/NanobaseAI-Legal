package com.nanobase.specai.compliance.application;

/**
 * Compliance semantic model routing modes.
 *
 * <p>Profile codes themselves remain free-form catalog values (FAST/BALANCED).
 */
public enum ComplianceRoutingMode {
    /** Authoritative profile only (current production default until FAST is ready). */
    BALANCED_ONLY,
    /** Run FAST in parallel for comparison; live decision always uses live profile. */
    SHADOW,
    /** Prefer FAST; escalate to live/BALANCED on low confidence, contradiction, or failure. */
    LIVE_FAST;

    public static ComplianceRoutingMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BALANCED_ONLY;
        }
        return ComplianceRoutingMode.valueOf(raw.trim().toUpperCase());
    }
}
