package com.zntsns.awardtrace.enrichment.internal;

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
 * Scores one model and prompt version on the gold set and exits, with status 1 if it failed. Start it as
 * {@code eval/README.md} shows: the {@code enricher} profile and {@code --awardtrace.enrichment.task=eval}.
 */
@Component
@Profile("enricher")
@ConditionalOnProperty(name = "awardtrace.enrichment.task", havingValue = "eval")
class EvalTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalTask.class);

    private final Evaluation evaluation;
    private final ConfigurableApplicationContext context;

    EvalTask(Evaluation evaluation, ConfigurableApplicationContext context) {
        this.evaluation = evaluation;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            EvalReport report = evaluation.run();
            log.info("Scored {} {} on {} descriptions with {} requests for ${}", report.model(),
                    report.promptVersion(), report.rows().size(), report.requests(), report.cost());
        } catch (Exception e) {
            log.error("Evaluation failed", e);
            exitCode = 1;
        }
        int status = exitCode;
        System.exit(SpringApplication.exit(context, () -> status));
    }
}
