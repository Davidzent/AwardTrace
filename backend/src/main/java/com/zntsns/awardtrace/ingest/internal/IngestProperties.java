package com.zntsns.awardtrace.ingest.internal;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param archiveUrl the USAspending award data archive, a public S3 bucket listing
 * @param agencies toptier agency codes to ingest, such as {@code 012}; empty means every agency
 * @param task {@code backfill}, {@code delta}, or {@code replay} to run once and exit; unset in long-running roles
 * @param apiUrl the USAspending API, which generates subaward files on request (ADR 0014)
 * @param downloadPollInterval how often to ask whether a requested file is ready
 * @param downloadDeadline how long to wait for a requested file before failing the run
 */
@ConfigurationProperties("awardtrace.ingest")
record IngestProperties(URI archiveUrl, Set<String> agencies, String task, URI apiUrl, Duration downloadPollInterval,
        Duration downloadDeadline) {

    IngestProperties {
        agencies = agencies == null ? Set.of() : Set.copyOf(agencies);
    }
}
