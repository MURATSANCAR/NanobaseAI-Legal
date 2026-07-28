package com.nanobase.specai.workflow.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationAdapter implements NotificationChannelAdapter {
    @Override
    public boolean supports(String channelConceptCode) {
        return "IN_APP".equals(channelConceptCode);
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        return new DeliveryResult(true, "in-app:" + UUID.randomUUID(), null);
    }
}
