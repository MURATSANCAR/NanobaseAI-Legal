package com.nanobase.specai.tender.api;

import com.nanobase.specai.compliance.application.GapAnalysisService;
import com.nanobase.specai.decision.application.BidDecisionEngine;
import com.nanobase.specai.decision.application.BidDecisionService;
import com.nanobase.specai.decision.application.TenderSummaryService;
import com.nanobase.specai.obligation.application.ObligationGenerationService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.tender.application.ProjectAccessService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenders/{projectId}")
public class TenderIntelligenceController {
    private final TenderSummaryService summaryService;
    private final BidDecisionService bidDecisionService;
    private final GapAnalysisService gapAnalysisService;
    private final ObligationGenerationService obligationService;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;

    public TenderIntelligenceController(TenderSummaryService summaryService,
                                        BidDecisionService bidDecisionService,
                                        GapAnalysisService gapAnalysisService,
                                        ObligationGenerationService obligationService,
                                        ProjectAccessService access,
                                        CurrentTenant currentTenant) {
        this.summaryService = summaryService;
        this.bidDecisionService = bidDecisionService;
        this.gapAnalysisService = gapAnalysisService;
        this.obligationService = obligationService;
        this.access = access;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/summary")
    Map<String, Object> summary(@PathVariable UUID projectId) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return summaryService.get(principal.tenantId(), projectId);
    }

    @PostMapping("/summary/rebuild")
    @ResponseStatus(HttpStatus.OK)
    Map<String, Object> rebuildSummary(@PathVariable UUID projectId) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return summaryService.rebuild(principal.tenantId(), projectId);
    }

    @GetMapping("/gaps")
    List<Map<String, Object>> gaps(@PathVariable UUID projectId) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return gapAnalysisService.listGaps(principal.tenantId(), projectId);
    }

    @GetMapping("/bid-decision")
    Map<String, Object> bidDecision(@PathVariable UUID projectId) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return bidDecisionService.get(principal.tenantId(), projectId);
    }

    @PostMapping("/bid-decision/recommend")
    Map<String, Object> recommend(@PathVariable UUID projectId,
                                  @RequestBody BidRecommendRequest request) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return bidDecisionService.recommendAndPersist(principal.tenantId(), projectId,
            new BidDecisionEngine.DecisionInput(
                request.mandatoryTotal(), request.mandatoryCompliant(),
                request.mandatoryNonCompliant(), request.mandatoryUnknown(),
                request.unresolvedHardBlockers(), request.remediableBeforeBidGaps(),
                request.openClarifications(), request.criticalRisks(),
                request.criticalContractualRisks(), request.allMandatoryCompliant(),
                request.financialAdequacy(), request.technicalAdequacy()),
            principal.subject());
    }

    @PostMapping("/bid-decision/approve")
    Map<String, Object> approve(@PathVariable UUID projectId,
                                @RequestBody BidApproveRequest request) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        return bidDecisionService.approve(principal.tenantId(), projectId,
            request.finalDecision(), request.overrideReason(), principal.subject());
    }

    @PostMapping("/award/obligations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> generateObligations(@PathVariable UUID projectId) {
        var principal = currentTenant.require();
        access.requireView(projectId, principal);
        UUID contractId = obligationService.generateFromAward(
            principal.tenantId(), projectId, principal.subject());
        return Map.of("contractId", contractId, "projectId", projectId);
    }

    public record BidRecommendRequest(
        int mandatoryTotal,
        int mandatoryCompliant,
        int mandatoryNonCompliant,
        int mandatoryUnknown,
        int unresolvedHardBlockers,
        int remediableBeforeBidGaps,
        int openClarifications,
        int criticalRisks,
        int criticalContractualRisks,
        boolean allMandatoryCompliant,
        boolean financialAdequacy,
        boolean technicalAdequacy
    ) {
    }

    public record BidApproveRequest(
        @NotBlank String finalDecision,
        String overrideReason
    ) {
    }
}
