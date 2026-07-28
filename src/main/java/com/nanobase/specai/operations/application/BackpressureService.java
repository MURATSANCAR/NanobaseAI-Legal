package com.nanobase.specai.operations.application;

import java.util.UUID;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BackpressureService {
    private final RabbitAdmin rabbit;
    private final int delayDepth;
    private final int rejectDepth;
    private final boolean failClosed;

    public BackpressureService(
        RabbitAdmin rabbit,
        @Value("${specai.backpressure.document.delay-queue-depth:100}") int delayDepth,
        @Value("${specai.backpressure.document.reject-queue-depth:500}") int rejectDepth,
        @Value("${specai.backpressure.fail-closed:true}") boolean failClosed
    ) {
        if (delayDepth < 0 || rejectDepth <= delayDepth) {
            throw new IllegalArgumentException("Backpressure queue thresholds are invalid");
        }
        this.rabbit = rabbit;
        this.delayDepth = delayDepth;
        this.rejectDepth = rejectDepth;
        this.failClosed = failClosed;
    }

    public Decision documentUpload(UUID organizationId) {
        try {
            QueueInformation queue = rabbit.getQueueInfo("document-processing.request");
            if (queue == null) {
                return failClosed ? Decision.REJECT_TEMPORARILY : Decision.ACCEPT;
            }
            int depth = queue.getMessageCount();
            if (depth >= rejectDepth) {
                return Decision.REJECT_TEMPORARILY;
            }
            if (depth >= delayDepth) {
                return Decision.ACCEPT_WITH_DELAY;
            }
            return Decision.ACCEPT;
        } catch (RuntimeException unavailable) {
            return failClosed ? Decision.REJECT_TEMPORARILY : Decision.ACCEPT;
        }
    }

    public void requireDocumentCapacity(UUID organizationId) {
        Decision decision = documentUpload(organizationId);
        if (decision == Decision.REJECT_TEMPORARILY
            || decision == Decision.REQUIRE_ADMIN_OVERRIDE) {
            throw new WorkloadCapacityException(decision);
        }
    }

    public enum Decision {
        ACCEPT,
        ACCEPT_WITH_DELAY,
        QUEUE,
        REJECT_TEMPORARILY,
        REQUIRE_ADMIN_OVERRIDE
    }
}
