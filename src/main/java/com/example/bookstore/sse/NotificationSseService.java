package com.example.bookstore.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationSseService {

    // Map userId -> set of emitters (support multiple tabs)
    private final Map<Long, Set<SseEmitter>> clients = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // never timeout by default
        clients.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = clients.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                clients.remove(userId);
            }
        }
    }

    public boolean sendEventToUser(Long userId, String eventName, Object data) {
        Set<SseEmitter> set = clients.get(userId);
        if (set == null || set.isEmpty()) {
            return false;
        }

        boolean sentAny = false;
        for (SseEmitter emitter : set) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(eventName)
                        .data(data);
                emitter.send(event);
                sentAny = true;
            } catch (IOException e) {
                removeEmitter(userId, emitter);
            }
        }
        return sentAny;
    }

    public boolean sendEventToAll(String eventName, Object data) {
        boolean any = false;
        for (Long userId : clients.keySet()) {
            boolean ok = sendEventToUser(userId, eventName, data);
            any = any || ok;
        }
        return any;
    }

    /**
     * Get total number of connected SSE clients
     * Counts unique users + all tabs/connections
     */
    public long getConnectedClientCount() {
        return clients.values().stream()
                .mapToLong(Set::size)
                .sum();
    }

    /**
     * Get number of connected users (users with at least one connection)
     */
    public long getConnectedUserCount() {
        return clients.size();
    }
}
