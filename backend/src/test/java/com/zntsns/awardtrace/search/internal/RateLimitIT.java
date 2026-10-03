package com.zntsns.awardtrace.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs a real Tomcat, since resolving X-Forwarded-For is Tomcat's job, not MVC's. Requests come from 127.0.0.1, an
 * internal proxy address, as Caddy's do. Each test uses its own client addresses, since buckets outlive a test. The
 * requests fail validation before touching Elasticsearch or PostgreSQL, but still count. The clock is stopped: in real
 * time, a slow runner refills a token while a test sends its requests, and the request it expects refused gets through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "awardtrace.rate-limit.exempt-addresses=198.51.100.9")
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    private static final String SEARCH = "/api/v1/awards/search?page=0";
    private static final String OTHER = "/api/v1/recipients/SHORT";

    @TestConfiguration
    static class StoppedClock {

        @Bean
        @Primary
        Clock stoppedClock() {
            return Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void limitsSearchPerForwardedClientAddress() throws Exception {
        for (int i = 0; i < RateLimiting.SEARCH_PER_MINUTE; i++) {
            assertThat(get(SEARCH, "203.0.113.7").statusCode()).isEqualTo(400);
        }

        var limited = get(SEARCH, "203.0.113.7");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValue("1");
        assertThat(limited.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(limited.body()).contains("\"type\":\"rate-limited\"");
        // Tomcat reads X-Forwarded-For from the right, so an address the client prepended changes nothing.
        assertThat(get(SEARCH, "192.0.2.1, 203.0.113.7").statusCode()).isEqualTo(429);
        // Other clients, and the same client's other requests, have their own buckets.
        assertThat(get(SEARCH, "203.0.113.8").statusCode()).isEqualTo(400);
        assertThat(get(OTHER, "203.0.113.7").statusCode()).isEqualTo(400);
    }

    @Test
    void limitsOtherRequestsAtTheirOwnRate() throws Exception {
        for (int i = 0; i < RateLimiting.OTHER_PER_MINUTE; i++) {
            assertThat(get(OTHER, "203.0.113.20").statusCode()).isEqualTo(400);
        }

        assertThat(get(OTHER, "203.0.113.20").statusCode()).isEqualTo(429);
    }

    @Test
    void neverLimitsAnExemptAddress() throws Exception {
        for (int i = 0; i <= RateLimiting.SEARCH_PER_MINUTE; i++) {
            assertThat(get(SEARCH, "198.51.100.9").statusCode()).isEqualTo(400);
        }
    }

    private HttpResponse<String> get(String path, String forwardedFor) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-For", forwardedFor)
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
