package com.zntsns.awardtrace.outbox;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Outbox reads for other modules. */
@Service
public class OutboxQueries {

    /** @param oldestCreatedAt when the longest-waiting event was written; null when none are waiting */
    public record Backlog(long count, Instant oldestCreatedAt) {
    }

    private final JdbcClient jdbc;

    OutboxQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Award-changed events the relay hasn't published yet. A growing backlog means the relay is stuck. */
    public Backlog backlog() {
        return jdbc.sql("""
                SELECT count(*) AS count, min(created_at) AS oldest_created_at
                FROM outbox WHERE published_at IS NULL
                """)
                .query(Backlog.class)
                .single();
    }
}
