package com.nanobase.specai.workflow.application;

import com.nanobase.specai.workflow.application.NotificationChannelAdapter.DeliveryResult;
import com.nanobase.specai.workflow.application.NotificationChannelAdapter.NotificationMessage;

public interface EmailDeliveryGateway {
    DeliveryResult deliver(NotificationMessage message);
}
