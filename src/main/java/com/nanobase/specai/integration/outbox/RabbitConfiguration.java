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
    public static final String DOCUMENT_QUEUE = "document-processing.request";
    public static final String RESULT_QUEUE = "document-processing.result";
    public static final String DEAD_LETTER_QUEUE = "document-processing.dlq";
    public static final String DOCUMENT_ROUTING_KEY = "document.processing.requested.v1";
    public static final String STARTED_ROUTING_KEY = "document.processing.started.v1";
    public static final String PROGRESS_ROUTING_KEY = "document.processing.progress.v1";
    public static final String COMPLETED_ROUTING_KEY = "document.processing.completed.v1";
    public static final String FAILED_ROUTING_KEY = "document.processing.failed.v1";
    public static final String MANUAL_REVIEW_ROUTING_KEY =
        "document.processing.manual-review.v1";
    public static final String DEAD_ROUTING_KEY = "document.processing.dead";
    public static final String RETRY_30 = "document.retry.30s";
    public static final String RETRY_120 = "document.retry.120s";
    public static final String RETRY_600 = "document.retry.600s";
    public static final String REQUIREMENT_QUEUE = "requirement-extraction.request";
    public static final String ANALYSIS_PROFILE_CREATED =
        "analysis.profile.created.v1";
    public static final String REQUIREMENT_REQUESTED =
        "requirement.extraction.requested.v1";
    public static final String REQUIREMENT_STARTED =
        "requirement.extraction.started.v1";
    public static final String REQUIREMENT_PROGRESS =
        "requirement.extraction.progress.v1";
    public static final String REQUIREMENT_COMPLETED =
        "requirement.extraction.completed.v1";
    public static final String REQUIREMENT_FAILED =
        "requirement.extraction.failed.v1";
    public static final String REQUIREMENT_REVIEW =
        "requirement.review.required.v1";
    public static final String EXPERT_FEEDBACK =
        "expert.feedback.recorded.v1";
    public static final String TERMINOLOGY_CANDIDATE =
        "terminology.candidate.discovered.v1";
    public static final String RISK_ANALYSIS_QUEUE = "risk-analysis.request";
    public static final String RISK_ANALYSIS_REQUESTED = "risk.analysis.requested.v1";
    public static final String RISK_ANALYSIS_STARTED = "risk.analysis.started.v1";
    public static final String RISK_ANALYSIS_PROGRESS = "risk.analysis.progress.v1";
    public static final String RISK_ANALYSIS_COMPLETED = "risk.analysis.completed.v1";
    public static final String RISK_ANALYSIS_FAILED = "risk.analysis.failed.v1";
    public static final String RISK_REVIEW_REQUIRED = "risk.review.required.v1";
    public static final String AMBIGUITY_DETECTED = "ambiguity.detected.v1";
    public static final String CONFLICT_CANDIDATE_DETECTED =
        "conflict.candidate.detected.v1";
    public static final String DOCUMENT_CHANGE_SET_CREATED =
        "document.change-set.created.v1";
    public static final String IMPACT_ANALYSIS_REQUESTED =
        "impact.analysis.requested.v1";
    public static final String ANALYSIS_STALE_DETECTED =
        "analysis.stale.detected.v1";
    public static final String KNOWLEDGE_QUEUE = "knowledge-extraction.request";
    public static final String COMPLIANCE_QUEUE = "compliance-analysis.request";

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
    Queue resultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE).build();
    }

    @Bean
    Binding processingStartedBinding(Queue resultQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(resultQueue).to(specAiExchange).with(STARTED_ROUTING_KEY);
    }

    @Bean
    Binding processingProgressBinding(Queue resultQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(resultQueue).to(specAiExchange).with(PROGRESS_ROUTING_KEY);
    }

    @Bean
    Binding processingCompletedBinding(Queue resultQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(resultQueue).to(specAiExchange).with(COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding processingFailedBinding(Queue resultQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(resultQueue).to(specAiExchange).with(FAILED_ROUTING_KEY);
    }

    @Bean
    Binding processingManualReviewBinding(Queue resultQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(resultQueue).to(specAiExchange)
            .with(MANUAL_REVIEW_ROUTING_KEY);
    }

    @Bean
    Binding documentBinding(Queue documentQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(documentQueue).to(specAiExchange).with(DOCUMENT_ROUTING_KEY);
    }

    @Bean
    Queue requirementQueue() {
        return QueueBuilder.durable(REQUIREMENT_QUEUE).build();
    }

    @Bean
    Binding requirementBinding(Queue requirementQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(requirementQueue).to(specAiExchange)
            .with(REQUIREMENT_REQUESTED);
    }

    @Bean
    Queue riskAnalysisQueue() {
        return QueueBuilder.durable(RISK_ANALYSIS_QUEUE).build();
    }

    @Bean
    Binding riskAnalysisBinding(Queue riskAnalysisQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(riskAnalysisQueue).to(specAiExchange)
            .with(RISK_ANALYSIS_REQUESTED);
    }

    @Bean
    Queue knowledgeQueue() {
        return QueueBuilder.durable(KNOWLEDGE_QUEUE).build();
    }

    @Bean
    Binding knowledgeBinding(Queue knowledgeQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(knowledgeQueue).to(specAiExchange)
            .with("knowledge.extraction.requested.v1");
    }

    @Bean
    Queue complianceQueue() {
        return QueueBuilder.durable(COMPLIANCE_QUEUE).build();
    }

    @Bean
    Binding complianceBinding(Queue complianceQueue, DirectExchange specAiExchange) {
        return BindingBuilder.bind(complianceQueue).to(specAiExchange)
            .with("compliance.analysis.requested.v1");
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
