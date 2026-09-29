package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.internal.IngestRuns.Mode;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs one ingest and exits, with status 1 if it failed. Start it with
 * {@code --awardtrace.ingest.task=backfill}, {@code delta}, or {@code replay}.
 */
@Component
@ConditionalOnProperty("awardtrace.ingest.task")
class IngestTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestTask.class);

    private final IngestRuns runs;
    private final IngestProperties properties;
    private final ConfigurableApplicationContext context;

    IngestTask(IngestRuns runs, IngestProperties properties, ConfigurableApplicationContext context) {
        this.runs = runs;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            runs.run(Mode.valueOf(properties.task().toUpperCase(Locale.ROOT)));
        } catch (Exception e) {
            log.error("Ingest task {} failed", properties.task(), e);
            exitCode = 1;
        }
        int status = exitCode;
        System.exit(SpringApplication.exit(context, () -> status));
    }
}
