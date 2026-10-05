package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The live path's spend controls, around every request (doc 09). Before a request, the circuit breaker must allow
 * it, and today's spend plus the most the request could cost must stay within the daily cap: a request that could pass
 * the cap isn't sent, and opens the breaker. After a request, what it used is added to today's spend at the published
 * prices. An exception from the model counts as an error toward opening the breaker; a refusal or a rejected answer
 * doesn't, since the API did answer.
 */
class SpendGuard implements ClassificationModel {

    /** Thrown instead of sending a request the breaker or the cap forbids. Its descriptions stay unclassified. */
    static final class Refused extends RuntimeException {

        Refused(String message) {
            super(message);
        }
    }

    static final String LIVE = "live";

    private final ClassificationModel model;
    private final SpendLedger ledger;
    private final CircuitBreaker breaker;
    private final BigDecimal dailyCap;
    private final Clock clock;
    private final Prices prices;

    SpendGuard(ClassificationModel model, SpendLedger ledger, CircuitBreaker breaker, BigDecimal dailyCap,
            Clock clock) {
        this.model = model;
        this.ledger = ledger;
        this.breaker = breaker;
        this.dailyCap = dailyCap;
        this.clock = clock;
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
        if (!breaker.allowsCall()) {
            throw new Refused("The circuit breaker is open");
        }
        LocalDate today = LocalDate.now(clock);
        BigDecimal spent = ledger.spent(today, LIVE);
        BigDecimal maxCost = model.maxCost(items);
        if (spent.add(maxCost).compareTo(dailyCap) > 0) {
            breaker.open();
            throw new Refused("Today's spend of $%s plus up to $%s for this request would pass the daily cap of $%s"
                    .formatted(spent, maxCost, dailyCap));
        }
        Reply reply;
        try {
            reply = model.classify(items);
        } catch (RuntimeException e) {
            breaker.failed();
            throw e;
        }
        breaker.succeeded();
        ledger.add(today, LIVE, reply.usage(), prices.cost(reply.usage()));
        return reply;
    }
}
