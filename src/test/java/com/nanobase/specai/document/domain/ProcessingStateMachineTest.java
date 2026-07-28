package com.nanobase.specai.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingStateMachineTest {
    @Test
    void rejectsReadyToParsingAndPreservesTerminalJob() {
        assertThat(ProcessingStateMachine.canTransition(
            DocumentStatus.READY, DocumentStatus.PARSING)).isFalse();
        DocumentProcessingJob job = DocumentProcessingJob.queued(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        for (DocumentStatus status : new DocumentStatus[]{
            DocumentStatus.VIRUS_SCANNING, DocumentStatus.CLASSIFYING,
            DocumentStatus.QUEUED, DocumentStatus.PARSING,
            DocumentStatus.STRUCTURE_DETECTION, DocumentStatus.INDEXING,
            DocumentStatus.READY}) {
            job.transition(status, 50, null, null, null, Instant.now());
        }
        assertThatThrownBy(() -> job.transition(DocumentStatus.PARSING, 40,
            null, null, null, Instant.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedJobCanBeQueuedForRetry() {
        assertThat(ProcessingStateMachine.canTransition(
            DocumentStatus.FAILED, DocumentStatus.QUEUED)).isTrue();
    }
}
