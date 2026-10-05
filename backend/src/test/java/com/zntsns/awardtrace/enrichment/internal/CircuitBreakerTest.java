package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.enrichment.internal.CircuitBreaker.State;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final CircuitBreaker breaker = new CircuitBreaker(clock);

    @Test
    void opensAfterFiveConsecutiveErrors() {
        for (int i = 0; i < 4; i++) {
            breaker.failed();
        }
        assertThat(breaker.allowsCall()).isTrue();

        breaker.failed();

        assertThat(breaker.state()).isEqualTo(State.OPEN);
        assertThat(breaker.allowsCall()).isFalse();
    }

    @Test
    void countsOnlyConsecutiveErrors() {
        for (int i = 0; i < 4; i++) {
            breaker.failed();
        }
        breaker.succeeded();
        for (int i = 0; i < 4; i++) {
            breaker.failed();
        }

        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void halfOpensAfterFifteenMinutesForOneTrialCall() {
        breaker.open();
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(breaker.allowsCall()).isFalse();

        clock.advance(Duration.ofSeconds(1));

        assertThat(breaker.allowsCall()).isTrue();
        assertThat(breaker.state()).isEqualTo(State.HALF_OPEN);
        assertThat(breaker.allowsCall()).as("a second call while the trial is out").isFalse();
    }

    @Test
    void closesWhenTheTrialSucceeds() {
        breaker.open();
        clock.advance(Duration.ofMinutes(15));
        breaker.allowsCall();

        breaker.succeeded();

        assertThat(breaker.state()).isEqualTo(State.CLOSED);
        assertThat(breaker.allowsCall()).isTrue();
    }

    @Test
    void opensForAnotherFifteenMinutesWhenTheTrialFails() {
        breaker.open();
        clock.advance(Duration.ofMinutes(15));
        breaker.allowsCall();

        breaker.failed();

        assertThat(breaker.state()).isEqualTo(State.OPEN);
        clock.advance(Duration.ofMinutes(14));
        assertThat(breaker.allowsCall()).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(breaker.allowsCall()).isTrue();
    }
}
