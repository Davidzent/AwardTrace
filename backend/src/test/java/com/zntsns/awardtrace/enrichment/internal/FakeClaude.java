package com.zntsns.awardtrace.enrichment.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for the Messages API and its Message Batches. It answers every item a request numbers with one category,
 * as the real API would under structured output, and keeps each request's items, so integration tests run the real
 * client without reaching Anthropic (doc 09).
 */
final class FakeClaude {

    /** How every request of a batch ends, for the backfill's tests. */
    enum BatchResult {
        ANSWERED, ERRORED, REFUSED, MISSING_AN_ID
    }

    /** One submitted batch: each request's IDs, by custom ID, and how many times the batch has been checked. */
    record Batch(String id, Map<String, List<String>> requests, AtomicInteger checks) {
    }

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String BATCHES = "/v1/messages/batches";

    private final List<List<String>> requests = new CopyOnWriteArrayList<>();
    private final List<Batch> batches = new CopyOnWriteArrayList<>();
    private final HttpServer server;
    private volatile String category = "OTHER";
    private volatile boolean failing;
    private volatile BatchResult batchResult = BatchResult.ANSWERED;

    FakeClaude() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/messages", this::messages);
        server.createContext(BATCHES, this::batches);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The IDs each request sent, in order. */
    List<List<String>> requests() {
        return List.copyOf(requests);
    }

    /** The batches submitted, in order. */
    List<Batch> batches() {
        return List.copyOf(batches);
    }

    void answer(String category) {
        this.category = category;
    }

    /** Answers every request with a server error, which the client throws as an exception, until told otherwise. */
    void fail(boolean failing) {
        this.failing = failing;
    }

    void endBatchRequests(BatchResult result) {
        this.batchResult = result;
    }

    void reset() {
        requests.clear();
        batches.clear();
        category = "OTHER";
        failing = false;
        batchResult = BatchResult.ANSWERED;
    }

    void stop() {
        server.stop(0);
    }

    private void messages(HttpExchange exchange) throws IOException {
        List<String> ids = ids(JSON.readTree(exchange.getRequestBody()));
        requests.add(ids);
        if (failing) {
            respond(exchange, 500, JSON.writeValueAsBytes(Map.of("type", "error",
                    "error", Map.of("type", "api_error", "message", "Internal server error"))));
            return;
        }
        respond(exchange, 200, JSON.writeValueAsBytes(message(ids, "end_turn", ids)));
    }

    /** {@code POST /v1/messages/batches}, {@code GET .../{id}}, and {@code GET .../{id}/results}. */
    private void batches(HttpExchange exchange) throws IOException {
        String rest = exchange.getRequestURI().getPath().substring(BATCHES.length());
        if (rest.isEmpty() && exchange.getRequestMethod().equals("POST")) {
            var submitted = new LinkedHashMap<String, List<String>>();
            for (JsonNode request : JSON.readTree(exchange.getRequestBody()).path("requests")) {
                submitted.put(request.path("custom_id").asString(), ids(request.path("params")));
            }
            var batch = new Batch("msgbatch_" + (batches.size() + 1), submitted, new AtomicInteger());
            batches.add(batch);
            respond(exchange, 200, JSON.writeValueAsBytes(batchObject(batch, "in_progress")));
            return;
        }
        String id = rest.substring(1).replace("/results", "");
        Batch batch = batches.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
        if (rest.endsWith("/results")) {
            String lines = batch.requests().entrySet().stream()
                    .map(request -> JSON.writeValueAsString(Map.of("custom_id", request.getKey(),
                            "result", result(request.getValue()))))
                    .collect(Collectors.joining("\n"));
            exchange.getResponseHeaders().add("Content-Type", "application/x-jsonl");
            byte[] body = lines.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        // The first check finds the batch still processing, so the backfill has to wait for it.
        String status = batch.checks().getAndIncrement() == 0 ? "in_progress" : "ended";
        respond(exchange, 200, JSON.writeValueAsBytes(batchObject(batch, status)));
    }

    private Map<String, Object> result(List<String> ids) {
        return switch (batchResult) {
            case ANSWERED -> Map.of("type", "succeeded", "message", message(ids, "end_turn", ids));
            case REFUSED -> Map.of("type", "succeeded", "message", message(ids, "refusal", List.of()));
            case MISSING_AN_ID -> Map.of("type", "succeeded", "message", message(ids, "end_turn",
                    ids.subList(1, ids.size())));
            case ERRORED -> Map.of("type", "errored", "error", Map.of("type", "error",
                    "error", Map.of("type", "api_error", "message", "Internal server error")));
        };
    }

    /** A reply billed as 900 input tokens plus 50 an item, and 30 output tokens an item. */
    private Map<String, Object> message(List<String> ids, String stopReason, List<String> answered) {
        var content = new ArrayList<Map<String, Object>>();
        if (!stopReason.equals("refusal")) {
            String answer = JSON.writeValueAsString(Map.of("items", answered.stream()
                    .map(id -> Map.of("id", id, "category", category, "confidence", 0.9))
                    .toList()));
            content.add(Map.of("type", "text", "text", answer));
        }
        return Map.of(
                "id", "msg_fake", "type", "message", "role", "assistant", "model", "claude-haiku-4-5",
                "content", content,
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 900 + 50 * ids.size(), "output_tokens", 30 * ids.size()));
    }

    private Map<String, Object> batchObject(Batch batch, String status) {
        boolean ended = status.equals("ended");
        int count = batch.requests().size();
        var body = new LinkedHashMap<String, Object>();
        body.put("id", batch.id());
        body.put("type", "message_batch");
        body.put("processing_status", status);
        body.put("request_counts", Map.of("processing", ended ? 0 : count, "succeeded", ended ? count : 0,
                "errored", 0, "canceled", 0, "expired", 0));
        body.put("created_at", "2026-10-05T12:00:00Z");
        body.put("expires_at", "2026-10-06T12:00:00Z");
        body.put("ended_at", ended ? "2026-10-05T12:05:00Z" : null);
        body.put("cancel_initiated_at", null);
        body.put("archived_at", null);
        body.put("results_url", ended ? url() + BATCHES + "/" + batch.id() + "/results" : null);
        return body;
    }

    /** Each line of a request's user message is "1. {"id": ..., "text": ...}". */
    private static List<String> ids(JsonNode params) {
        String content = params.path("messages").path(0).path("content").asString();
        return content.lines()
                .map(line -> JSON.readTree(line.substring(line.indexOf(". ") + 2)).path("id").asString())
                .toList();
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
