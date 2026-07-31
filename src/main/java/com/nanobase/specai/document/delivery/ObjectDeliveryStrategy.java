package com.nanobase.specai.document.delivery;

public interface ObjectDeliveryStrategy {
    ObjectDeliveryResult createDelivery(ObjectDeliveryContext context);
}
