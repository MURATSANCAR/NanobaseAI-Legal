package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface SupportTicketAdapter {
    boolean supports(String providerConceptCode);

    ExternalTicket synchronize(
        UUID feedbackCaseId,
        String externalTicketId,
        JsonNode sanitizedPayload
    );

    record ExternalTicket(String externalTicketId, String externalStatus) {
    }
}
