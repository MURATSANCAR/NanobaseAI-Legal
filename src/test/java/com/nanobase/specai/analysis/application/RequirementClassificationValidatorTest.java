package com.nanobase.specai.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.domain.ConsequenceType;
import com.nanobase.specai.analysis.domain.LifecycleStage;
import com.nanobase.specai.analysis.domain.ObligationLevel;
import com.nanobase.specai.compliance.application.ClosedWorldValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequirementClassificationValidatorTest {
    @Mock
    private ClosedWorldValidator closedWorld;

    @Test
    void mandatoryWithDisqualificationIsConsistent() {
        var validator = new RequirementClassificationValidator(closedWorld);
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        candidate.put("obligationLevel", "MANDATORY");
        candidate.put("consequenceType", "DISQUALIFICATION");
        candidate.put("lifecycleStage", "BID_SUBMISSION");
        var result = validator.validate(
            RequirementClassificationValidator.ClassificationProposal.fromCandidate(candidate),
            UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.obligationLevel()).isEqualTo(ObligationLevel.MANDATORY);
        assertThat(result.consequenceType()).isEqualTo(ConsequenceType.DISQUALIFICATION);
        assertThat(result.status()).isEqualTo(RequirementClassificationValidator.Status.SUCCEEDED);
    }

    @Test
    void preferredWithDisqualificationRequiresReview() {
        var validator = new RequirementClassificationValidator(closedWorld);
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        candidate.put("obligationLevel", "PREFERRED");
        candidate.put("consequenceType", "DISQUALIFICATION");
        var result = validator.validate(
            RequirementClassificationValidator.ClassificationProposal.fromCandidate(candidate),
            UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.status())
            .isEqualTo(RequirementClassificationValidator.Status.REVIEW_REQUIRED);
    }

    @Test
    void postAwardWithBidRejectionRequiresReview() {
        var validator = new RequirementClassificationValidator(closedWorld);
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        candidate.put("lifecycleStage", "POST_AWARD");
        candidate.put("consequenceType", "BID_REJECTION");
        var result = validator.validate(
            RequirementClassificationValidator.ClassificationProposal.fromCandidate(candidate),
            UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.lifecycleStage()).isEqualTo(LifecycleStage.POST_AWARD);
        assertThat(result.status())
            .isEqualTo(RequirementClassificationValidator.Status.REVIEW_REQUIRED);
    }

    @Test
    void invalidEnumFallsBackToUnknown() {
        var validator = new RequirementClassificationValidator(closedWorld);
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        candidate.put("obligationLevel", "SUPER_MANDATORY");
        candidate.put("criticality", "ULTRA");
        var result = validator.validate(
            RequirementClassificationValidator.ClassificationProposal.fromCandidate(candidate),
            UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.obligationLevel()).isEqualTo(ObligationLevel.UNKNOWN);
        assertThat(result.criticality().name()).isEqualTo("UNKNOWN");
    }

    @Test
    void closedWorldClaimWithoutDeclarationIsNotApplied() {
        when(closedWorld.hasActiveDeclaration(any(), any(), isNull())).thenReturn(false);
        var validator = new RequirementClassificationValidator(closedWorld);
        ObjectNode candidate = new ObjectMapper().createObjectNode();
        candidate.put("closedWorldRequired", true);
        var result = validator.validate(
            RequirementClassificationValidator.ClassificationProposal.fromCandidate(candidate),
            UUID.randomUUID(), UUID.randomUUID());
        assertThat(result.closedWorldRequired()).isFalse();
        assertThat(result.status())
            .isEqualTo(RequirementClassificationValidator.Status.REVIEW_REQUIRED);
    }
}
