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
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** A stand-in for the USAspending archive: an S3-style paged listing plus the files it lists. */
final class FakeArchive {

    private static final String BASE = "/award_data_archive/";

    private final NavigableMap<String, byte[]> files = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicInteger> downloads = new ConcurrentHashMap<>();
    private final HttpServer server;
    private volatile int pageSize = 1000;
    private volatile boolean failingListings;

    FakeArchive() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext(BASE, this::handle);
        server.start();
    }

    URI url() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + BASE);
    }

    FakeArchive put(String name, byte[] content) {
        files.put(name, content);
        return this;
    }

    int downloads(String name) {
        return downloads.getOrDefault(name, new AtomicInteger()).get();
    }

    void pageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    void failListings(boolean failing) {
        this.failingListings = failing;
    }

    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String name = exchange.getRequestURI().getPath().substring(BASE.length());
        if (name.isEmpty()) {
            respond(exchange, failingListings ? 500 : 200, listing(query(exchange)).getBytes(StandardCharsets.UTF_8));
        } else if (files.containsKey(name)) {
            downloads.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
            respond(exchange, 200, files.get(name));
        } else {
            respond(exchange, 404, new byte[0]);
        }
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

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
