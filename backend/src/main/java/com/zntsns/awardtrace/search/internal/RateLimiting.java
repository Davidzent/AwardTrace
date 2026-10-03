package com.zntsns.awardtrace.search.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate limits per client address (doc 07): one token bucket for search, which is what loads Elasticsearch, and one
 * for everything else. A client may burst up to a minute's allowance, then continues at the per-minute rate.
 *
 * <p>Behind Caddy, the client address comes from X-Forwarded-For. Tomcat resolves it only when the request arrives
 * from an internal proxy address ({@code server.forward-headers-strategy: native}), reading from the right, so a
 * client can't pick its own address by sending the header itself.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
@EnableConfigurationProperties(RateLimiting.Properties.class)
class RateLimiting implements WebMvcConfigurer {

    static final int SEARCH_PER_MINUTE = 60;
    static final int OTHER_PER_MINUTE = 120;
    private static final String[] SEARCH = {"/api/v1/awards/search", "/api/v1/recipients/*/awards"};
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();

    /** @param exemptAddresses clients never limited, such as the load generator (doc 12) */
    @ConfigurationProperties("awardtrace.rate-limit")
    record Properties(Set<String> exemptAddresses) {

        Properties {
            exemptAddresses = exemptAddresses == null ? Set.of() : Set.copyOf(exemptAddresses);
        }
    }

    private record Bucket(double tokens, long updatedAt) {
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Set<String> exempt;
    // The application clock rather than System.nanoTime, so a test can stop time and no bucket refills mid-test.
    private final Clock clock;

    RateLimiting(Properties properties, Clock clock) {
        this.exempt = properties.exemptAddresses();
        this.clock = clock;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(limit("search", SEARCH_PER_MINUTE)).addPathPatterns(SEARCH);
        registry.addInterceptor(limit("other", OTHER_PER_MINUTE))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(SEARCH);
    }

    private HandlerInterceptor limit(String kind, int perMinute) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String client = request.getRemoteAddr();
                if (!exempt.contains(client)) {
                    long waitMillis = take(kind + " " + client, perMinute);
                    if (waitMillis > 0) {
                        throw tooManyRequests(kind, perMinute, waitMillis);
                    }
                }
                return true;
            }
        };
    }

    /** Takes a token if one is left; otherwise returns how long until one will be, in milliseconds. */
    private long take(String key, int perMinute) {
        long now = clock.millis();
        double perMilli = (double) perMinute / MINUTE;
        long[] wait = {0};
        buckets.compute(key, (ignored, bucket) -> {
            double tokens = bucket == null
                    ? perMinute
                    // The wall clock can step back; that only delays a refill.
                    : Math.min(perMinute, bucket.tokens() + Math.max(0, now - bucket.updatedAt()) * perMilli);
            if (tokens >= 1) {
                return new Bucket(tokens - 1, now);
            }
            wait[0] = (long) Math.ceil((1 - tokens) / perMilli);
            return new Bucket(tokens, now);
        });
        return wait[0];
    }

    /** A bucket untouched for a minute has refilled completely, which is the same as having none. */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    void evictIdle() {
        long cutoff = clock.millis() - MINUTE;
        buckets.values().removeIf(bucket -> bucket.updatedAt() < cutoff);
    }

    private static ErrorResponseException tooManyRequests(String kind, int perMinute, long waitMillis) {
        long seconds = Math.max(1, (waitMillis + 999) / 1000);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Up to %d %s requests a minute are allowed; retry in %d s".formatted(perMinute, kind, seconds));
        problem.setType(URI.create("rate-limited"));
        problem.setTitle("Too many requests");
        var exception = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS, problem, null);
        exception.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        return exception;
    }
}
