package com.nanobase.specai.integration.outbox;

import java.util.concurrent.TimeUnit;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private final OutboxStore store;
    private final RabbitTemplate rabbit;
    private final PlatformMetrics metrics;

    public OutboxPublisher(OutboxStore store, RabbitTemplate rabbit, PlatformMetrics metrics) {
        this.store = store;
        this.rabbit = rabbit;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${specai.outbox.interval-ms:1000}")
    public void publishPending() {
        OutboxStore.ClaimBatch batch = store.claimBatch();
        metrics.outboxClaimed(batch.events().size(), batch.reclaimedCount());
        for (OutboxEvent event : batch.events()) {
            try {
                CorrelationData correlation = new CorrelationData(event.id().toString());
                rabbit.convertAndSend(RabbitConfiguration.EXCHANGE, event.routingKey(),
                    event.payloadJson(), message -> {
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setMessageId(event.id().toString());
                        message.getMessageProperties().setHeader("x-retry-count", 0);
                        return message;
                    }, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("Broker rejected event: " + confirm.getReason());
                }
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException("Broker returned unroutable event");
                }
                store.published(event.id());
            } catch (Exception exception) {
                metrics.outboxPublishFailed();
                if (store.failed(event.id(), exception.getMessage()) == OutboxStatus.DEAD) {
                    metrics.outboxDead();
                }
            }
        }
    }
}
