package com.nanobase.specai.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.workflow.application.SafeReportRenderers.DocxRenderer;
import com.nanobase.specai.workflow.application.SafeReportRenderers.PdfRenderer;
import com.nanobase.specai.workflow.application.SafeReportRenderers.XlsxRenderer;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyContext;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalVote;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentCandidate;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentContext;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentPolicyVersion;
import com.nanobase.specai.workflow.application.WorkflowModels.BusinessCalendarDefinition;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportContext;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportFactor;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphNode;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphTransition;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowSimulationInput;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicWorkflowEnginesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private SafeJsonWorkflowConditionEngine conditions;

    @BeforeEach
    void setUp() {
        conditions = new SafeJsonWorkflowConditionEngine(mapper);
    }

    @Test
    void safeDslEvaluatesNestedFactsWithoutExecutingCode() throws Exception {
        JsonNode expression = mapper.readTree("""
            {"all":[
              {"field":"project.criticalRiskCount","operator":"EQUAL","value":0},
              {"field":"project.pendingReviewCount","operator":"LESS_THAN_OR_EQUAL","value":1},
              {"field":"user.roles","operator":"CONTAINS","value":"LEGAL_REVIEWER"}
            ]}
            """);
        var result = conditions.evaluate(new WorkflowConditionContext(Map.of(
            "project", Map.of("criticalRiskCount", 0, "pendingReviewCount", 1),
            "user", Map.of("roles", List.of("LEGAL_REVIEWER")))), expression);
        assertThat(result.matched()).isTrue();
        assertThatThrownBy(() -> conditions.evaluate(
            new WorkflowConditionContext(Map.of()), mapper.readTree("""
                {"field":"java.lang.Runtime","operator":"EXECUTE","value":"calc"}
                """))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulationDetectsParallelReachabilityAndConditionDeadEnds() throws Exception {
        UUID start = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        UUID end = UUID.randomUUID();
        JsonNode entry = mapper.readTree("{\"entry\":true,\"parallel\":true}");
        JsonNode normal = mapper.readTree("{}");
        JsonNode terminal = mapper.readTree("{\"terminal\":true}");
        JsonNode always = mapper.readTree("{}");
        var simulation = new WorkflowSimulationService(conditions).simulate(
            new WorkflowSimulationInput(
                List.of(
                    new WorkflowGraphNode(start, "start", "CUSTOM_START", entry),
                    new WorkflowGraphNode(left, "left", "CUSTOM_TASK", normal),
                    new WorkflowGraphNode(right, "right", "CUSTOM_REVIEW", normal),
                    new WorkflowGraphNode(end, "end", "CUSTOM_END", terminal)),
                List.of(
                    transition(start, left, always),
                    transition(start, right, always),
                    transition(left, end, always),
                    transition(right, end, always)),
                new WorkflowConditionContext(Map.of()),
                Set.of("CUSTOM_START", "CUSTOM_TASK", "CUSTOM_REVIEW", "CUSTOM_END"),
                Set.of(), 3));
        assertThat(simulation.valid()).isTrue();
        assertThat(simulation.visitedNodeCodes()).contains("left", "right", "end");
    }

    @Test
    void assignmentPolicyRejectsConflictsAndSelectsLowestWorkload() throws Exception {
        JsonNode policy = mapper.readTree("""
            {"requiredRoles":["LEGAL_REVIEWER"],"maximumWorkload":0.9,
             "rejectConflictOfInterest":true,"weights":{"availability":1.0}}
            """);
        var conflicted = candidate("u1", 0.1, true);
        var available = candidate("u2", 0.2, false);
        var busy = candidate("u3", 0.8, false);
        var result = new ConfigurableAssignmentPolicyEngine().resolve(
            new AssignmentContext(UUID.randomUUID(), UUID.randomUUID(), "REVIEW",
                "HIGH", null, null, null, List.of(conflicted, available, busy), Map.of()),
            new AssignmentPolicyVersion(UUID.randomUUID(), policy));
        assertThat(result.assignedUserId()).isEqualTo("u2");
        assertThat(result.manualResolutionRequired()).isFalse();
    }

    @Test
    void countPercentageAndWeightedApprovalArePolicyConfigured() throws Exception {
        JsonNode count = mapper.readTree("""
            {"aggregation":"COUNT","threshold":2,"negativeThreshold":1,
             "positiveDecisionConcepts":["YES"],"negativeDecisionConcepts":["NO"]}
            """);
        var engine = new ConfigurableApprovalPolicyEngine();
        var approved = engine.evaluate(new ApprovalPolicyContext("TENANT_MODE", 3,
            List.of(new ApprovalVote("a", "YES", 1),
                new ApprovalVote("b", "YES", 1)), Map.of()), count);
        assertThat(approved.approved()).isTrue();

        JsonNode weighted = mapper.readTree("""
            {"aggregation":"WEIGHT","threshold":2.5,"negativeThreshold":3,
             "positiveDecisionConcepts":["ACCEPT"],"negativeDecisionConcepts":["DECLINE"]}
            """);
        var weightedResult = engine.evaluate(new ApprovalPolicyContext("CUSTOM_WEIGHTED", 4,
            List.of(new ApprovalVote("a", "ACCEPT", 2),
                new ApprovalVote("b", "ACCEPT", 1)), Map.of()), weighted);
        assertThat(weightedResult.approved()).isTrue();
        assertThat(weightedResult.positiveWeight()).isEqualTo(3);
    }

    @Test
    void businessCalendarSkipsWeekendAndHoliday() throws Exception {
        JsonNode config = mapper.readTree("""
            {"workingDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
             "workdayStart":"09:00","workdayEnd":"17:00"}
            """);
        Instant friday = ZonedDateTime.of(2026, 7, 31, 16, 0, 0, 0,
            ZoneOffset.UTC).toInstant();
        var result = new BusinessCalendarService().calculate(friday, 120, 0.5,
            new BusinessCalendarDefinition("UTC", config,
                Map.of(LocalDate.of(2026, 8, 3), "HOLIDAY")));
        assertThat(result.targetDueAt()).isEqualTo(
            ZonedDateTime.of(2026, 8, 4, 10, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void slaSchedulerUsesPolicyStatusesAndPrefersBreachOverWarning() throws Exception {
        UUID open = UUID.randomUUID();
        UUID warned = UUID.randomUUID();
        UUID breached = UUID.randomUUID();
        JsonNode policy = mapper.readTree("""
            {"warningStatusConceptId":"%s","breachedStatusConceptId":"%s"}
            """.formatted(warned, breached));
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        var warning = Sprint7SlaScheduler.resolveAction(now,
            now.minusSeconds(1), now.plusSeconds(60), open, policy);
        assertThat(warning.statusConceptId()).isEqualTo(warned);
        assertThat(warning.eventType()).isEqualTo("task.sla.warning.v1");
        var breach = Sprint7SlaScheduler.resolveAction(now,
            now.minusSeconds(60), now.minusSeconds(1), warned, policy);
        assertThat(breach.statusConceptId()).isEqualTo(breached);
        assertThat(breach.eventType()).isEqualTo("task.sla.breached.v1");
    }

    @Test
    void policyGateAndDecisionSupportRemainExplainableAndHumanControlled()
        throws Exception {
        JsonNode finalization = mapper.readTree("""
            {"rules":[{"code":"NO_OPEN_TASKS","severity":"BLOCKING",
              "condition":{"field":"project.openTasks","operator":"EQUAL","value":0}}]}
            """);
        var gate = new ConfigurablePolicyGate(conditions);
        assertThat(gate.evaluate(Map.of("project", Map.of("openTasks", 2)),
            finalization).passed()).isFalse();
        JsonNode policy = mapper.readTree("""
            {"defaultConfidence":0.6,"decisionBands":[
              {"minimumScore":-1,"maximumScore":-0.01,
               "decisionConceptCode":"MANUAL","requiresExecutiveReview":true},
              {"minimumScore":0,"maximumScore":1,
               "decisionConceptCode":"CONDITIONAL","requiresExecutiveReview":true}]}
            """);
        var decision = new ConfigurableDecisionSupportPolicyEngine().evaluate(
            new DecisionSupportContext(Map.of("confidence", 0.8),
                List.of(new DecisionSupportFactor("READINESS", 0.5, 1,
                    "Verified readiness", Map.of("snapshotId", "s1")))), policy);
        assertThat(decision.recommendedDecisionConceptCode()).isEqualTo("CONDITIONAL");
        assertThat(decision.requiresExecutiveReview()).isTrue();
        assertThat(decision.explanation()).contains("READINESS: Verified readiness");
    }

    @Test
    void notificationSanitizerRemovesSensitiveContent() throws Exception {
        JsonNode raw = mapper.readTree("""
            {"projectName":"Alpha","taskCode":"T-1","dueAt":"2026-08-01",
             "documentText":"secret","evidenceText":"secret","token":"secret",
             "signedUrl":"https://private"}
            """);
        JsonNode safe = new NotificationPayloadSanitizer().sanitize(raw);
        assertThat(safe.path("projectName").asText()).isEqualTo("Alpha");
        assertThat(safe.has("documentText")).isFalse();
        assertThat(safe.has("evidenceText")).isFalse();
        assertThat(safe.has("token")).isFalse();
        assertThat(safe.has("signedUrl")).isFalse();
    }

    @Test
    void reportRenderersProducePdfDocxAndXlsxArtifacts() throws Exception {
        JsonNode report = mapper.readTree("""
            {"snapshotId":"s1","sections":[
              {"code":"SUMMARY","title":"Summary","data":{"riskCount":2}}]}
            """);
        byte[] pdf = new PdfRenderer().render("Report", report).content();
        byte[] docx = new DocxRenderer().render("Report", report).content();
        byte[] xlsx = new XlsxRenderer().render("Report", report).content();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(docx[0]).isEqualTo((byte) 'P');
        assertThat(docx[1]).isEqualTo((byte) 'K');
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }

    private WorkflowGraphTransition transition(UUID source, UUID target, JsonNode condition) {
        return new WorkflowGraphTransition(UUID.randomUUID(), source, target, condition, 0);
    }

    private AssignmentCandidate candidate(String user, double workload, boolean conflict) {
        return new AssignmentCandidate(user, null, Set.of(),
            Set.of("LEGAL_REVIEWER"), null, null, workload, true, conflict, Map.of());
    }
}
