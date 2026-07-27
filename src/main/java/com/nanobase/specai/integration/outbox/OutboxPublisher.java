package com.nanobase.specai.integration.outbox;

import java.time.Clock;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final RabbitTemplate rabbit;
    private final Clock clock = Clock.systemUTC();

    public OutboxPublisher(OutboxEventRepository events, RabbitTemplate rabbit) {
        this.events = events;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${specai.outbox.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : events
            .findTop50ByPublishedAtIsNullAndRetryCountLessThanOrderByCreatedAt(10)) {
            try {
                rabbit.convertAndSend(RabbitConfiguration.EXCHANGE, event.routingKey(), event.payload(),
                    message -> {
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setMessageId(event.id().toString());
                        return message;
                    });
                event.published(clock.instant());
            } catch (RuntimeException exception) {
                event.failed();
            }
        }
    }
}
