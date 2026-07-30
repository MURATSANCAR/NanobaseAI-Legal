package com.nanobase.specai.compliance.api;

import com.nanobase.specai.shared.security.CurrentTenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessment-reviews")
public class AssessmentReviewController {
    private final JdbcTemplate jdbc;
    private final CurrentTenant currentTenant;

    public AssessmentReviewController(JdbcTemplate jdbc, CurrentTenant currentTenant) {
        this.jdbc = jdbc;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    List<Map<String, Object>> list(@org.springframework.web.bind.annotation.RequestParam UUID assessmentId) {
        var principal = currentTenant.require();
        return jdbc.queryForList("""
            select * from assessment_review
             where organization_id = ? and assessment_id = ?
             order by created_at desc
            """, principal.tenantId(), assessmentId);
    }

    @PostMapping
    Map<String, Object> create(@RequestBody CreateReviewRequest request) {
        var principal = currentTenant.require();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into assessment_review (
                id, organization_id, assessment_id, reviewer_user_id, review_decision,
                review_comment, override_decision, override_reason, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
            """, id, principal.tenantId(), request.assessmentId(), principal.subject(),
            request.reviewDecision(), request.reviewComment(), request.overrideDecision(),
            request.overrideReason());
        return Map.of("id", id, "assessmentId", request.assessmentId());
    }

    public record CreateReviewRequest(
        UUID assessmentId,
        String reviewDecision,
        String reviewComment,
        String overrideDecision,
        String overrideReason
    ) {
    }
}
