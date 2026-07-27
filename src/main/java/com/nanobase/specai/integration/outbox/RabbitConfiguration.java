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
    public static final String DEAD_LETTER_EXCHANGE = "specai.events.dlx";
    public static final String DOCUMENT_QUEUE = "specai.document.processing";
    public static final String DEAD_LETTER_QUEUE = "specai.document.processing.failed";

    @Bean
    DirectExchange specAiExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE)
            .deadLetterExchange("specai.events.dlx")
            .deadLetterRoutingKey("document.failed")
            .build();
    }

    @Bean
    Binding documentBinding(Queue documentQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(documentQueue).to(specAiExchange).with("document.uploaded");
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("document.failed");
    }
}
