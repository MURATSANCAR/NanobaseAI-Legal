package com.nanobase.specai.document.delivery;

import java.net.URI;

public record ObjectDeliveryResult(
    ObjectDeliveryMode mode,
    URI url,
    boolean fallbackUsed,
    String publicHost,
    String telemetryCode
) {
}
