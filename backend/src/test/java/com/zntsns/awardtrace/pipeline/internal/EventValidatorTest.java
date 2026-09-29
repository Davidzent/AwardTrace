package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.pipeline.internal.TestEvents.deletion;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.file;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import org.junit.jupiter.api.Test;

class EventValidatorTest {

    private final ContractTransactionIngested valid =
            transaction("0", "2026-07-16", "27500.00", "27500.00", file("20260909"));

    @Test
    void acceptsACompleteEvent() {
        assertThat(EventValidator.problem(valid)).isEmpty();
        assertThat(EventValidator.problem(deletion("0", file("20260909")))).isEmpty();
    }

    @Test
    void namesTheFirstProblemFound() {
        assertThat(EventValidator.problem(edited("\"recipient_uei\":\"MN5KRX2W9R46\",", ""))).hasValue("MISSING_UEI");
        assertThat(EventValidator.problem(edited("\"recipient_uei\":\"MN5KRX2W9R46\"", "\"recipient_uei\":\"MN5KRX\"")))
                .hasValue("INVALID_UEI");
        assertThat(EventValidator.problem(edited("\"award_type\":\"C\"", "\"award_type\":\"IDV_B\"")))
                .hasValue("UNKNOWN_AWARD_TYPE");
        assertThat(EventValidator.problem(edited("\"total_obligated\":\"27500.00\",", "")))
                .hasValue("MISSING_TOTAL_OBLIGATED");
    }

    /** The valid event with one piece of its JSON replaced, as a malformed producer would send it. */
    private ContractTransactionIngested edited(String from, String to) {
        String json = EventCodec.write(EventEnvelope.of(valid, null));
        assertThat(json).contains(from);
        return EventCodec.read(json.replace(from, to), ContractTransactionIngested.class).payload();
    }
}
