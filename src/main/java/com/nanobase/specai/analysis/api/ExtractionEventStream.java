package com.nanobase.specai.analysis.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ExtractionEventStream {
    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        subscribers.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> remove(jobId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(ignored -> remove.run());
        return emitter;
    }

    public void publish(UUID jobId, String eventType, Object payload) {
        for (SseEmitter emitter : subscribers.getOrDefault(jobId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(payload));
            } catch (IOException | IllegalStateException exception) {
                remove(jobId, emitter);
            }
        }
    }

    public void complete(UUID jobId) {
        List<SseEmitter> emitters = subscribers.remove(jobId);
        if (emitters != null) {
            emitters.forEach(SseEmitter::complete);
        }
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(jobId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(jobId, emitters);
            }
        }
    }
}
