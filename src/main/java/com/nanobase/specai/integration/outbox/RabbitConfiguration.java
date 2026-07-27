package com.nanobase.specai.integration.outbox;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String EXCHANGE = "specai.events";
    public static final String RETRY_EXCHANGE = "specai.events.retry";
    public static final String DEAD_LETTER_EXCHANGE = "specai.events.dlx";
    public static final String DOCUMENT_QUEUE = "document-processing";
    public static final String DEAD_LETTER_QUEUE = "document-processing.dlq";
    public static final String DOCUMENT_ROUTING_KEY = "document.uploaded.v1";
    public static final String DEAD_ROUTING_KEY = "document.failed";
    public static final String RETRY_30 = "document.retry.30s";
    public static final String RETRY_120 = "document.retry.120s";
    public static final String RETRY_600 = "document.retry.600s";

    @Bean
    DirectExchange specAiExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE).build();
    }

    @Bean
    Binding documentBinding(Queue documentQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(documentQueue).to(specAiExchange).with(DOCUMENT_ROUTING_KEY);
    }

    @Bean
    Queue retry30Queue() {
        return retryQueue("document-processing.retry.30s", 30_000);
    }

    @Bean
    Queue retry120Queue() {
        return retryQueue("document-processing.retry.120s", 120_000);
    }

    @Bean
    Queue retry600Queue() {
        return retryQueue("document-processing.retry.600s", 600_000);
    }

    @Bean
    Binding retry30Binding(Queue retry30Queue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retry30Queue).to(retryExchange).with(RETRY_30);
    }

    @Bean
    Binding retry120Binding(Queue retry120Queue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retry120Queue).to(retryExchange).with(RETRY_120);
    }

    @Bean
    Binding retry600Binding(Queue retry600Queue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retry600Queue).to(retryExchange).with(RETRY_600);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_ROUTING_KEY);
    }

    private Queue retryQueue(String name, int ttlMilliseconds) {
        return QueueBuilder.durable(name)
            .ttl(ttlMilliseconds)
            .deadLetterExchange(EXCHANGE)
            .deadLetterRoutingKey(DOCUMENT_ROUTING_KEY)
            .build();
    }
}
