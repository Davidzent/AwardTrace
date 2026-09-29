package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A stand-in for the two USAspending endpoints: the download request and its status. */
class SubawardDownloadsTest {

    private static final String FILE_URL = "https://files.usaspending.gov/generated_downloads/All_Subawards_1.zip";

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger requestStatus = new AtomicInteger(200);
    private final ConcurrentLinkedQueue<String> statuses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger polls = new AtomicInteger();
    private final HttpServer api;

    SubawardDownloadsTest() throws IOException {
        api = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        api.createContext("/api/v2/bulk_download/awards/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, requestStatus.get(), requestStatus.get() == 200
                    ? "{\"status_url\": \"" + url() + "api/v2/download/status?file_name=All_Subawards_1.zip\"}"
                    : "{\"detail\": \"Invalid Parameter: date_range total days must be within a year\"}");
        });
        api.createContext("/api/v2/download/status", exchange -> {
            polls.incrementAndGet();
            String status = statuses.size() > 1 ? statuses.poll() : statuses.peek();
            respond(exchange, 200, "{\"status\": \"" + status + "\", \"message\": \"database timeout\", \"file_url\": \""
                    + FILE_URL + "\"}");
        });
        api.start();
    }

    @AfterEach
    void stop() {
        api.stop(0);
    }

    @Test
    void requestsOneAgencyAndYearThenWaitsForTheFile() throws Exception {
        statuses.addAll(List.of("ready", "running", "finished"));

        URI file = downloads(Duration.ofSeconds(5)).generate("Department of Agriculture", LocalDate.of(2025, 10, 1),
                LocalDate.of(2026, 9, 30));

        assertThat(file).isEqualTo(URI.create(FILE_URL));
        assertThat(polls).hasValue(3);
        var filters = JsonMapper.shared().readTree(requestBody.get()).path("filters");
        assertThat(filters.path("sub_award_types").path(0).asString()).isEqualTo("procurement");
        assertThat(filters.path("agencies").path(0).path("name").asString()).isEqualTo("Department of Agriculture");
        assertThat(filters.path("agencies").path(0).path("type").asString()).isEqualTo("awarding");
        assertThat(filters.path("date_range").path("start_date").asString()).isEqualTo("2025-10-01");
        assertThat(filters.path("date_range").path("end_date").asString()).isEqualTo("2026-09-30");
    }

    @Test
    void failsWhenUsaspendingFailsToGenerateTheFile() {
        statuses.add("failed");

        assertThatThrownBy(() -> generate(Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("database timeout");
    }

    @Test
    void givesUpAtTheDeadline() {
        statuses.add("running");

        assertThatThrownBy(() -> generate(Duration.ofMillis(50)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Gave up after PT0.05S");
    }

    @Test
    void reportsWhyTheApiRefusedTheRequest() {
        requestStatus.set(400);

        assertThatThrownBy(() -> generate(Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("returned 400")
                .hasMessageContaining("date_range total days must be within a year");
    }

    private void generate(Duration deadline) throws Exception {
        downloads(deadline).generate("Department of Agriculture", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 30));
    }

    private SubawardDownloads downloads(Duration deadline) {
        return new SubawardDownloads(new IngestProperties(null, Set.of(), null, url(), Duration.ofMillis(1), deadline));
    }

    private URI url() {
        return URI.create("http://localhost:" + api.getAddress().getPort() + "/");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
