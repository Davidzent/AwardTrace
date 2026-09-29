package com.zntsns.awardtrace.ingest.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Has USAspending generate a file of reported procurement subawards, then waits for it (ADR 0014). Generation is
 * asynchronous: the request returns a status URL, which is polled until the file is ready, has failed, or the
 * deadline passes.
 */
@Component
class SubawardDownloads {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final URI apiUrl;
    private final Duration pollInterval;
    private final Duration deadline;

    SubawardDownloads(IngestProperties properties) {
        this.apiUrl = properties.apiUrl();
        this.pollInterval = properties.downloadPollInterval();
        this.deadline = properties.downloadDeadline();
    }

    /**
     * The URL of a generated file holding every subaward that the agency's prime recipients reported with an action
     * in the range. The API accepts at most a year, so callers ask for one fiscal year at a time.
     */
    URI generate(String agencyName, LocalDate start, LocalDate end) throws IOException, InterruptedException {
        String body = JSON.writeValueAsString(Map.of(
                "file_format", "csv",
                "filters", Map.of(
                        "sub_award_types", List.of("procurement"),
                        "agencies", List.of(Map.of("type", "awarding", "tier", "toptier", "name", agencyName)),
                        "date_type", "action_date",
                        "date_range", Map.of("start_date", start.toString(), "end_date", end.toString()))));
        JsonNode requested = send(HttpRequest.newBuilder(apiUrl.resolve("api/v2/bulk_download/awards/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        URI statusUrl = URI.create(requested.path("status_url").asString());

        Instant giveUpAt = Instant.now().plus(deadline);
        while (true) {
            JsonNode status = send(HttpRequest.newBuilder(statusUrl).GET());
            switch (status.path("status").asString("")) {
                case "finished" -> {
                    return URI.create(status.path("file_url").asString());
                }
                case "failed" -> throw new IOException("USAspending failed to generate " + statusUrl + ": "
                        + status.path("message").asString(""));
                default -> {
                    if (Instant.now().isAfter(giveUpAt)) {
                        throw new IOException("Gave up after " + deadline + " waiting for " + statusUrl);
                    }
                    Thread.sleep(pollInterval);
                }
            }
        }
    }

    private JsonNode send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.header("User-Agent", SourceFileStore.USER_AGENT).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // The API explains a refused request in its body, such as a date range over a year.
            throw new IOException(response.request().method() + " " + response.request().uri() + " returned "
                    + response.statusCode() + ": " + response.body());
        }
        return JSON.readTree(response.body());
    }
}
