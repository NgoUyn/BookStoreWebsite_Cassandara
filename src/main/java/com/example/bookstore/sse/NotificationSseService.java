package com.example.bookstore.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationSseService {

    /** Thời lượng tối đa 1 phiên SSE; hết hạn server đóng để client reconnect và giải phóng emitter. */
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    /** Chu kỳ heartbeat: giữ kết nối qua proxy/trình duyệt khi không có notification nào. */
    private static final long HEARTBEAT_MS = 25_000L;

    // Map userId -> set of emitters (support multiple tabs)
    private final Map<Long, Set<SseEmitter>> clients = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "notification-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter register(Long userId) {
        // Có timeout hữu hạn (thay vì 0L/never) để không giữ kết nối treo vô hạn
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        clients.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        // Heartbeat dạng comment (":keep-alive") -> client EventSource bỏ qua, không tạo event rác
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                removeEmitter(userId, emitter);
            }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);

        Runnable cleanup = () -> {
            heartbeat.cancel(false);
            removeEmitter(userId, emitter);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        heartbeats.shutdownNow();
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
