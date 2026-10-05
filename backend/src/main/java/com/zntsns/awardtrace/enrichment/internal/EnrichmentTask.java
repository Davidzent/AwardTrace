package com.zntsns.awardtrace.enrichment.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs one enrichment task and exits, with status 1 if it failed (doc 09): {@code eval} scores a model and prompt
 * version on the gold set, and {@code backfill} classifies every unclassified description through Message Batches.
 * Start it with the {@code enricher} profile and {@code --awardtrace.enrichment.task}, as {@code eval/README.md}
 * shows. Without a task, or with {@code live}, the enricher listens for changed awards instead.
 */
@Component
@Profile("enricher")
@ConditionalOnExpression("'${awardtrace.enrichment.task:live}' != 'live'")
class EnrichmentTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentTask.class);

    private final String task;
    private final Evaluation evaluation;
    private final Backfill backfill;
    private final ConfigurableApplicationContext context;

    EnrichmentTask(@Value("${awardtrace.enrichment.task}") String task, Evaluation evaluation, Backfill backfill,
            ConfigurableApplicationContext context) {
        this.task = task;
        this.evaluation = evaluation;
        this.backfill = backfill;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            switch (task) {
                case "eval" -> {
                    EvalReport report = evaluation.run();
                    log.info("Scored {} {} on {} descriptions with {} requests for ${}", report.model(),
                            report.promptVersion(), report.rows().size(), report.requests(), report.cost());
                }
                case "backfill" -> {
                    Backfill.Result result = backfill.run();
                    log.info("Backfill: {} batches of {} requests classified {} descriptions for ${}; {} left"
                            + " unanswered", result.batches(), result.requests(), result.classified(), result.cost(),
                            result.unanswered());
                }
                default -> throw new IllegalArgumentException("No enrichment task " + task);
            }
        } catch (Exception e) {
            log.error("The {} task failed", task, e);
            exitCode = 1;
        }
        int status = exitCode;
        System.exit(SpringApplication.exit(context, () -> status));
    }
}
