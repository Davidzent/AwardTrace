package com.zntsns.awardtrace.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventCodecTest {

    record Payload(String awardId, BigDecimal federalActionObligation, LocalDate actionDate, Instant modifiedAt,
            String description) {
    }

    private final Payload payload = new Payload("CONT_AWD_1", new BigDecimal("-12500.00"), LocalDate.of(2026, 9, 14),
            Instant.parse("2026-09-20T03:11:48Z"), null);

    @Test
    void writesSnakeCaseWithMoneyAsExactDecimalStrings() {
        String json = EventCodec.write(EventEnvelope.of(payload, new EventEnvelope.Source(UuidV7.next(), "raw/x", 7)));

        assertThat(json)
                .contains("\"event_type\":\"Payload\"", "\"schema_version\":1", "\"row_number\":7")
                .contains("\"award_id\":\"CONT_AWD_1\"", "\"federal_action_obligation\":\"-12500.00\"")
                .contains("\"action_date\":\"2026-09-14\"", "\"modified_at\":\"2026-09-20T03:11:48Z\"")
                .doesNotContain("description");
    }

    @Test
    void readsBackWhatItWrites() {
        EventEnvelope<Payload> envelope = EventEnvelope.of(payload, null);

        EventEnvelope<Payload> read = EventCodec.read(EventCodec.write(envelope), Payload.class);

        assertThat(read).isEqualTo(envelope);
        assertThat(read.payload().federalActionObligation().scale()).isEqualTo(2);
    }

    @Test
    void generatesVersion7UuidsThatSortByTime() throws InterruptedException {
        UUID first = UuidV7.next();
        Thread.sleep(2);
        UUID second = UuidV7.next();

        assertThat(first.version()).isEqualTo(7);
        assertThat(first.variant()).isEqualTo(2);
        assertThat(first.getMostSignificantBits() >>> 16).isCloseTo(System.currentTimeMillis(), within(1_000L));
        assertThat(Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits())).isNegative();
    }
}
