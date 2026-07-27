package com.nanobase.specai.document.application;

import com.nanobase.specai.document.api.DocumentContracts.ProcessingEvent;
import com.nanobase.specai.document.domain.DocumentStatus;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ProcessingEventPublisher {
    private static final long TIMEOUT_MILLISECONDS = 5 * 60 * 1000L;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers =
        new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public SseEmitter subscribe(UUID documentId, UUID versionId, DocumentStatus status) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLISECONDS);
        subscribers.computeIfAbsent(documentId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(documentId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        send(emitter, new ProcessingEvent(documentId, versionId, status,
            progress(status), message(status), clock.instant()));
        return emitter;
    }

    public void publish(UUID documentId, UUID versionId, DocumentStatus status) {
        ProcessingEvent event = new ProcessingEvent(documentId, versionId, status,
            progress(status), message(status), clock.instant());
        for (SseEmitter emitter : List.copyOf(
            subscribers.getOrDefault(documentId, new CopyOnWriteArrayList<>()))) {
            if (!send(emitter, event)) {
                remove(documentId, emitter);
            } else if (status.terminal()) {
                emitter.complete();
                remove(documentId, emitter);
            }
        }
    }

    private boolean send(SseEmitter emitter, ProcessingEvent event) {
        try {
            emitter.send(SseEmitter.event().name("processing").data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            return false;
        }
    }

    private void remove(UUID documentId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> values = subscribers.get(documentId);
        if (values != null) {
            values.remove(emitter);
            if (values.isEmpty()) {
                subscribers.remove(documentId, values);
            }
        }
    }

    private int progress(DocumentStatus status) {
        return switch (status) {
            case UPLOADED -> 5;
            case VIRUS_SCANNING -> 15;
            case CLASSIFYING -> 25;
            case PARSING -> 40;
            case OCR_PROCESSING -> 55;
            case STRUCTURE_DETECTION -> 70;
            case INDEXING -> 90;
            case READY, FAILED, MANUAL_REVIEW_REQUIRED -> 100;
        };
    }

    private String message(DocumentStatus status) {
        return switch (status) {
            case UPLOADED -> "Doküman işleme kuyruğuna alındı";
            case VIRUS_SCANNING -> "Doküman güvenlik taramasından geçiriliyor";
            case CLASSIFYING -> "Doküman sınıflandırılıyor";
            case PARSING -> "Doküman metni ayrıştırılıyor";
            case OCR_PROCESSING -> "OCR işlemi uygulanıyor";
            case STRUCTURE_DETECTION -> "Doküman yapısı belirleniyor";
            case INDEXING -> "Doküman indeksleniyor";
            case READY -> "Doküman hazır";
            case FAILED -> "Doküman işlenemedi";
            case MANUAL_REVIEW_REQUIRED -> "Manuel inceleme gerekiyor";
        };
    }
}
