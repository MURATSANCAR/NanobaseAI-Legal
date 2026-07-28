package com.nanobase.specai.workflow.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationAdapter implements NotificationChannelAdapter {
    private final List<EmailDeliveryGateway> gateways;

    public EmailNotificationAdapter(List<EmailDeliveryGateway> gateways) {
        this.gateways = List.copyOf(gateways);
    }

    @Override
    public boolean supports(String channelConceptCode) {
        return "EMAIL".equals(channelConceptCode);
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        if (gateways.isEmpty()) {
            return new DeliveryResult(false, null, "EMAIL_GATEWAY_NOT_CONFIGURED");
        }
        return gateways.getFirst().deliver(message);
    }
}
