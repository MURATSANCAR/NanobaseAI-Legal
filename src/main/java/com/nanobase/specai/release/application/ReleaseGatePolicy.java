package com.nanobase.specai.release.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReleaseGatePolicy {
    public static final Set<String> STATUSES = Set.of("PASS", "FAIL", "WAIVED", "NOT_RUN");

    public GateEvaluation evaluate(
        List<String> requiredGateCodes,
        Map<String, String> actualStatuses,
        boolean manifestPresent,
        long openBlockers,
        boolean humanApprovalComplete
    ) {
        List<String> missing = new ArrayList<>();
        if (!manifestPresent) {
            missing.add("RELEASE_MANIFEST");
        }
        if (openBlockers > 0) {
            missing.add("OPEN_RELEASE_BLOCKERS=" + openBlockers);
        }
        requiredGateCodes.forEach(code -> {
            String status = actualStatuses.get(code);
            if (status == null || status.isBlank() || !STATUSES.contains(status)) {
                missing.add(code + ":MISSING");
            } else if ("FAIL".equals(status) || "NOT_RUN".equals(status)) {
                missing.add(code + ":" + status);
            }
        });
        if (!humanApprovalComplete) {
            missing.add("HUMAN_APPROVAL");
        }
        return new GateEvaluation(missing.isEmpty(), List.copyOf(missing));
    }

    public void validateWaiver(
        String status,
        String waiverReason,
        int compensatingControlCount,
        boolean authorized
    ) {
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported gate status: " + status);
        }
        if ("WAIVED".equals(status)
            && (!authorized || waiverReason == null || waiverReason.isBlank()
                || compensatingControlCount == 0)) {
            throw new IllegalArgumentException(
                "A waiver requires an authorized actor, reason and compensating control");
        }
    }

    public record GateEvaluation(boolean eligible, List<String> missingEvidence) {
    }
}
