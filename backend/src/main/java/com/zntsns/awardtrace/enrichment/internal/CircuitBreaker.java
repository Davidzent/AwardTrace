package com.zntsns.awardtrace.enrichment.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Stops the enricher's calls to the Claude API after 5 consecutive errors, or when the daily cap is reached (doc 09).
 * Open, it refuses every call. After 15 minutes it half-opens and lets one trial call through: a trial that succeeds
 * closes it, and one that fails opens it for another 15 minutes.
 */
class CircuitBreaker {

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    static final int CONSECUTIVE_ERRORS_TO_OPEN = 5;
    static final Duration OPEN_FOR = Duration.ofMinutes(15);

    private final Clock clock;
    private State state = State.CLOSED;
    private int consecutiveErrors;
    private Instant openedAt;

    CircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    /** Whether a call may go out now. Once the open period ends, the first caller to ask makes the trial call. */
    synchronized boolean allowsCall() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(OPEN_FOR))) {
            state = State.HALF_OPEN;
            return true;
        }
        return state == State.CLOSED;
    }

    synchronized void succeeded() {
        state = State.CLOSED;
        consecutiveErrors = 0;
    }

    synchronized void failed() {
        consecutiveErrors++;
        if (state == State.HALF_OPEN || consecutiveErrors >= CONSECUTIVE_ERRORS_TO_OPEN) {
            open();
        }
    }

    /** Opens at once, as reaching the cap does. */
    synchronized void open() {
        state = State.OPEN;
        openedAt = clock.instant();
    }

    synchronized State state() {
        return state;
    }
}
