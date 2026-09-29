package com.zntsns.awardtrace.indexer.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the search index once and exits, with status 1 if it failed. Start it with the {@code indexer} profile
 * and {@code --awardtrace.indexer.task=reindex}.
 */
@Component
@Profile("indexer")
@ConditionalOnProperty(name = "awardtrace.indexer.task", havingValue = "reindex")
class ReindexTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReindexTask.class);

    private final Reindexer reindexer;
    private final ConfigurableApplicationContext context;

    ReindexTask(Reindexer reindexer, ConfigurableApplicationContext context) {
        this.reindexer = reindexer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            reindexer.reindex();
        } catch (Exception e) {
            log.error("Reindex failed", e);
            exitCode = 1;
        }
        int status = exitCode;
        System.exit(SpringApplication.exit(context, () -> status));
    }
}
