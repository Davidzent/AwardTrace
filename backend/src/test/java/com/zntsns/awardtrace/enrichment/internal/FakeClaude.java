package com.zntsns.awardtrace.enrichment.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for the Messages API. It answers every item a request numbers with one category, as the real API would
 * under structured output, and keeps each request's items, so integration tests run the real client without reaching
 * Anthropic (doc 09).
 */
final class FakeClaude {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final List<List<String>> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;
    private volatile String category = "OTHER";
    private volatile boolean failing;

    FakeClaude() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/messages", this::messages);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The IDs each request sent, in order. */
    List<List<String>> requests() {
        return List.copyOf(requests);
    }

    void answer(String category) {
        this.category = category;
    }

    /** Answers every request with a server error, which the client throws as an exception, until told otherwise. */
    void fail(boolean failing) {
        this.failing = failing;
    }

    void reset() {
        requests.clear();
        category = "OTHER";
        failing = false;
    }

    void stop() {
        server.stop(0);
    }

    private void messages(HttpExchange exchange) throws IOException {
        String content = JSON.readTree(exchange.getRequestBody()).path("messages").path(0).path("content").asString();
        // Each line is "1. {"id": ..., "text": ...}".
        List<String> ids = content.lines()
                .map(line -> JSON.readTree(line.substring(line.indexOf(". ") + 2)).path("id").asString())
                .toList();
        requests.add(ids);
        if (failing) {
            respond(exchange, 500, JSON.writeValueAsBytes(Map.of("type", "error",
                    "error", Map.of("type", "api_error", "message", "Internal server error"))));
            return;
        }
        String answer = JSON.writeValueAsString(Map.of("items", ids.stream()
                .map(id -> Map.of("id", id, "category", category, "confidence", 0.9))
                .toList()));
        byte[] body = JSON.writeValueAsBytes(Map.of(
                "id", "msg_fake", "type", "message", "role", "assistant", "model", "claude-haiku-4-5",
                "content", List.of(Map.of("type", "text", "text", answer)),
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 900 + 50 * ids.size(), "output_tokens", 30 * ids.size())));
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
