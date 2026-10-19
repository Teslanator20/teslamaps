package com.teslamaps.features.secretsync;

import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Text websocket with whole-message callbacks and a single send queue. */
class SocketClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String name;
    private final ExecutorService queue;
    private volatile WebSocket socket;
    private volatile boolean connecting;
    private volatile long retryAt;

    SocketClient(String name) {
        this.name = name;
        this.queue = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TeslaMaps " + name + " socket");
            t.setDaemon(true);
            return t;
        });
    }

    boolean open() {
        WebSocket s = socket;
        return s != null && !s.isInputClosed() && !s.isOutputClosed();
    }

    boolean connecting() {
        return connecting;
    }

    void connect(String url, Map<String, String> headers, Consumer<String> onMessage, Runnable onOpen) {
        if (open() || connecting || System.currentTimeMillis() < retryAt) return;
        connecting = true;
        queue.submit(() -> {
            try {
                WebSocket.Builder b = HTTP.newWebSocketBuilder();
                headers.forEach(b::header);
                socket = b.buildAsync(URI.create(url), new Listener(onMessage)).get();
                TeslaMaps.LOGGER.info("[{}] connected", name);
                onOpen.run();
            } catch (Exception e) {
                if (e.getCause() instanceof java.net.http.WebSocketHandshakeException h) {
                    TeslaMaps.LOGGER.warn("[{}] handshake refused {}: {}", name, h.getResponse().statusCode(), h.getResponse().body());
                } else {
                    TeslaMaps.LOGGER.warn("[{}] connect failed: {}", name, e.toString());
                }
                socket = null;
                retryAt = System.currentTimeMillis() + 30_000;
            } finally {
                connecting = false;
            }
        });
    }

    void send(String text) {
        queue.submit(() -> {
            WebSocket s = socket;
            if (s == null || s.isOutputClosed()) return;
            try {
                s.sendText(text, true).join();
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[{}] send failed: {}", name, e.toString());
            }
        });
    }

    void close() {
        WebSocket s = socket;
        socket = null;
        if (s != null) queue.submit(() -> {
            try {
                s.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        });
    }

    private class Listener implements WebSocket.Listener {
        private final Consumer<String> onMessage;
        private final StringBuilder parts = new StringBuilder();

        Listener(Consumer<String> onMessage) {
            this.onMessage = onMessage;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            parts.append(data);
            if (last) {
                String msg = parts.toString();
                parts.setLength(0);
                try {
                    onMessage.accept(msg);
                } catch (Throwable t) {
                    TeslaMaps.LOGGER.warn("[{}] bad message: {}", name, t.toString());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            TeslaMaps.LOGGER.info("[{}] closed {} {}", name, status, reason);
            if (socket == ws) socket = null;
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            TeslaMaps.LOGGER.warn("[{}] error: {}", name, error.toString());
            if (socket == ws) socket = null;
        }
    }
}
