package com.zntsns.awardtrace.shared;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;
import tools.jackson.databind.JsonNode;
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

    /**
     * Reads an envelope whose payload type depends on its {@code event_type}.
     *
     * @throws IllegalArgumentException if the event type isn't one of {@code payloadTypes}
     * @throws tools.jackson.core.JacksonException if the JSON is malformed or doesn't fit the payload type
     */
    public static EventEnvelope<?> read(String json, Map<String, Class<?>> payloadTypes) {
        JsonNode tree = MAPPER.readTree(json);
        String eventType = tree.path("event_type").asString(null);
        Class<?> payloadType = payloadTypes.get(eventType);
        if (payloadType == null) {
            throw new IllegalArgumentException("Unknown event type " + eventType);
        }
        return MAPPER.treeToValue(tree,
                MAPPER.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType));
    }
}
