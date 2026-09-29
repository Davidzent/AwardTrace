package com.zntsns.awardtrace.ingest.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for USAspending: the award data archive's paged S3 listing and its files, and the API that names
 * agencies and generates subaward files (ADR 0014). Every generated file holds the subaward fixture under a new name,
 * so its bytes differ from the last one's, as real generated files do.
 */
final class FakeUsaspending {

    private static final String ARCHIVE = "/award_data_archive/";
    private static final String GENERATED = "/generated_downloads/";

    private final NavigableMap<String, byte[]> files = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicInteger> downloads = new ConcurrentHashMap<>();
    private final List<String> subawardRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger generated = new AtomicInteger();
    private final HttpServer server;
    private volatile int pageSize = 1000;
    private volatile boolean failingListings;
    private volatile boolean failingGeneration;

    FakeUsaspending() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext(ARCHIVE, this::archive);
        server.createContext(GENERATED, this::generatedFile);
        server.createContext("/api/v2/references/toptier_agencies/", exchange -> respond(exchange, 200, json("""
                {"results": [{"toptier_code": "012", "agency_name": "Department of Agriculture"},
                             {"toptier_code": "097", "agency_name": "Department of Defense"}]}""")));
        server.createContext("/api/v2/bulk_download/awards/", this::requestSubawards);
        server.createContext("/api/v2/download/status", this::status);
        server.start();
    }

    /** The archive's base URL, for {@code awardtrace.ingest.archive-url}. */
    URI url() {
        return URI.create(base() + ARCHIVE);
    }

    /** The API's base URL, for {@code awardtrace.ingest.api-url}. */
    URI apiUrl() {
        return URI.create(base() + "/");
    }

    FakeUsaspending put(String name, byte[] content) {
        files.put(name, content);
        return this;
    }

    int downloads(String name) {
        return downloads.getOrDefault(name, new AtomicInteger()).get();
    }

    /** Each subaward file requested, as "agency start end". */
    List<String> subawardRequests() {
        return List.copyOf(subawardRequests);
    }

    void pageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    void failListings(boolean failing) {
        this.failingListings = failing;
    }

    void failGeneration(boolean failing) {
        this.failingGeneration = failing;
    }

    void stop() {
        server.stop(0);
    }

    private void archive(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().substring(ARCHIVE.length());
        if (name.isEmpty()) {
            respond(exchange, failingListings ? 500 : 200, listing(query(exchange)).getBytes(StandardCharsets.UTF_8));
        } else if (files.containsKey(name)) {
            downloads.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
            respond(exchange, 200, files.get(name));
        } else {
            respond(exchange, 404, new byte[0]);
        }
    }

    private void requestSubawards(HttpExchange exchange) throws IOException {
        var filters = JsonMapper.shared().readTree(exchange.getRequestBody()).path("filters");
        subawardRequests.add(filters.path("agencies").path(0).path("name").asString() + " "
                + filters.path("date_range").path("start_date").asString() + " "
                + filters.path("date_range").path("end_date").asString());
        String name = "All_Subawards_" + generated.incrementAndGet() + ".zip";
        respond(exchange, 200, json("{\"status_url\": \"" + base() + "/api/v2/download/status?file_name=" + name + "\"}"));
    }

    private void status(HttpExchange exchange) throws IOException {
        String name = query(exchange).get("file_name");
        respond(exchange, 200, json(failingGeneration
                ? "{\"status\": \"failed\", \"message\": \"generation failed\"}"
                : "{\"status\": \"finished\", \"file_url\": \"" + base() + GENERATED + name + "\"}"));
    }

    private void generatedFile(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().substring(GENERATED.length()).replace(".zip", "");
        respond(exchange, 200, Fixtures.zip(name + "_Contracts_1.csv", Fixtures.subawardCsv()));
    }

    private String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String listing(Map<String, String> query) {
        String prefix = query.getOrDefault("prefix", "");
        var matching = files.tailMap(query.getOrDefault("marker", ""), false).keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList();
        var page = matching.stream().limit(pageSize).toList();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                + "<IsTruncated>" + (matching.size() > page.size()) + "</IsTruncated>"
                + page.stream().map(key -> "<Contents><Key>" + key + "</Key></Contents>").collect(Collectors.joining())
                + "</ListBucketResult>";
    }

    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return Map.of();
        }
        return Arrays.stream(raw.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0],
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }

    private static byte[] json(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
