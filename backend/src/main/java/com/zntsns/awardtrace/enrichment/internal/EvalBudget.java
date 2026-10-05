package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.util.List;

/**
 * The {@code eval} task's spend controls, kept in memory because the task writes nothing to the database: a request
 * that could take the run past its cap isn't sent. It also totals what the run used, for the report.
 */
final class EvalBudget implements ClassificationModel {

    private final ClassificationModel model;
    private final BigDecimal cap;
    private final Prices prices;
    private BigDecimal spent = BigDecimal.ZERO;
    private Usage used = new Usage(0, 0, 0, 0);
    private int requests;
    private int refusedRequests;

    EvalBudget(ClassificationModel model, BigDecimal cap) {
        this.model = model;
        this.cap = cap;
        this.prices = Prices.of(model.model());
    }

    @Override
    public String model() {
        return model.model();
    }

    @Override
    public String promptVersion() {
        return model.promptVersion();
    }

    @Override
    public BigDecimal maxCost(List<Item> items) {
        return model.maxCost(items);
    }

    @Override
    public Reply classify(List<Item> items) {
        BigDecimal maxCost = model.maxCost(items);
        if (spent.add(maxCost).compareTo(cap) > 0) {
            throw new IllegalStateException("The run has spent $%s; up to $%s more would pass its cap of $%s"
                    .formatted(spent, maxCost, cap));
        }
        Reply reply = model.classify(items);
        requests++;
        if (reply.stop() == Stop.REFUSAL) {
            refusedRequests++;
        }
        spent = spent.add(prices.cost(reply.usage()));
        used = new Usage(used.inputTokens() + reply.usage().inputTokens(),
                used.outputTokens() + reply.usage().outputTokens(),
                used.cacheWriteTokens() + reply.usage().cacheWriteTokens(),
                used.cacheReadTokens() + reply.usage().cacheReadTokens());
        return reply;
    }

    BigDecimal spent() {
        return spent;
    }

    Usage used() {
        return used;
    }

    int requests() {
        return requests;
    }

    int refusedRequests() {
        return refusedRequests;
    }
}
