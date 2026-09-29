package com.zntsns.awardtrace.pipeline.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** An event's record components as named SQL parameters. */
final class EventParameters {

    private EventParameters() {
    }

    /** Instants are bound as UTC offsets, which the PostgreSQL driver accepts. */
    static SqlParameterSource of(Object event) {
        return new SimplePropertySqlParameterSource(event) {
            @Override
            public Object getValue(String name) {
                Object value = super.getValue(name);
                return value instanceof Instant instant ? instant.atOffset(ZoneOffset.UTC) : value;
            }
        };
    }
}
