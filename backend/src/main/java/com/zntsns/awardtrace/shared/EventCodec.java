package com.zntsns.awardtrace.shared;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON wire format for events: snake_case names, money as decimal strings so no reader loses precision,
 * ISO-8601 dates and instants, and no null fields.
 */
public final class EventCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .withConfigOverride(BigDecimal.class,
                    override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
            .build();

    private EventCodec() {
    }

    public static String write(EventEnvelope<?> envelope) {
        return MAPPER.writeValueAsString(envelope);
    }

    public static <T> EventEnvelope<T> read(String json, Class<T> payloadType) {
        return MAPPER.readValue(json, MAPPER.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType));
    }
}
