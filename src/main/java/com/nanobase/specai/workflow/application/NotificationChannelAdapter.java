package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface NotificationChannelAdapter {
    boolean supports(String channelConceptCode);

    DeliveryResult send(NotificationMessage message);

    record NotificationMessage(
        String recipientReference,
        String subject,
        String body,
        JsonNode safeMetadata
    ) {
    }

    record DeliveryResult(boolean accepted, String providerMessageId, String errorCode) {
    }
}
