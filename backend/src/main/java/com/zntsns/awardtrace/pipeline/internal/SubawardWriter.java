package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.SubawardReported;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes reported subawards (ADR 0014). A report replaces the stored one only when it is newer, so replays and
 * repeats change nothing. When an in-scope award's subawards change, its index version is bumped and an outbox row
 * written in the same transaction, so its search document picks up the new subaward totals.
 */
@Component
class SubawardWriter {

    record Result(int applied, int awardsChanged) {
    }

    private static final String UPSERT_SUBAWARD = """
            INSERT INTO subaward (subaward_key, prime_award_id, prime_recipient_uei, prime_recipient_name,
                                  sub_recipient_uei, sub_recipient_name, subaward_number, amount, action_date,
                                  description, source_modified_at)
            VALUES (:subawardKey, :primeAwardId, :primeRecipientUei, :primeRecipientName, :subRecipientUei,
                    :subRecipientName, :subawardNumber, :amount, :actionDate, :description, :sourceModifiedAt)
            ON CONFLICT (subaward_key) DO UPDATE SET
                prime_award_id = EXCLUDED.prime_award_id, prime_recipient_uei = EXCLUDED.prime_recipient_uei,
                prime_recipient_name = EXCLUDED.prime_recipient_name, sub_recipient_uei = EXCLUDED.sub_recipient_uei,
                sub_recipient_name = EXCLUDED.sub_recipient_name, subaward_number = EXCLUDED.subaward_number,
                amount = EXCLUDED.amount, action_date = EXCLUDED.action_date, description = EXCLUDED.description,
                source_modified_at = EXCLUDED.source_modified_at, ingested_at = now()
            WHERE subaward.source_modified_at < EXCLUDED.source_modified_at
            """;

    // A revision can move a subaward to another prime award; both awards' totals change, but this bumps the new one
    // only. ponytail: revisions haven't been seen moving primes; track the previous prime if one ever does.
    private static final String BUMP_AWARDS = """
            WITH changed AS (
                UPDATE award SET index_version = index_version + 1, updated_at = now()
                WHERE award_id IN (:awardIds) AND deleted_at IS NULL
                RETURNING award_id, index_version
            )
            INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
            SELECT award_id, 'AwardChanged', 'SUBAWARD', index_version FROM changed
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SubawardWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    Result write(List<SubawardReported> subawards) {
        if (subawards.isEmpty()) {
            return new Result(0, 0);
        }
        int[] upserts = jdbc.batchUpdate(UPSERT_SUBAWARD,
                subawards.stream().map(EventParameters::of).toArray(SqlParameterSource[]::new));
        var changedAwards = new LinkedHashSet<String>();
        IntStream.range(0, upserts.length)
                .filter(i -> upserts[i] > 0)
                .forEach(i -> changedAwards.add(subawards.get(i).primeAwardId()));
        int awardsChanged = changedAwards.isEmpty()
                ? 0
                : jdbc.update(BUMP_AWARDS, new MapSqlParameterSource("awardIds", changedAwards));
        return new Result(Arrays.stream(upserts).sum(), awardsChanged);
    }

    /**
     * Recomputes the recipient network from every subaward, without blocking its readers.
     * ponytail: a full recompute per batch; maintain the edges incrementally if subaward volume makes it slow.
     */
    void refreshNetwork() {
        jdbc.getJdbcOperations().execute("REFRESH MATERIALIZED VIEW CONCURRENTLY recipient_edge");
    }
}
