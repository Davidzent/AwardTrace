package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a batch of transaction events in one database transaction (ADR 0012). A transaction row changes only for a
 * newer source file, deletes leave a marker, and every award and recipient the batch touches is recomputed from its
 * transactions. An award whose recomputed row differs gets a new {@code index_version} and one outbox event, so a
 * redelivered batch writes nothing.
 */
@Component
class TransactionWriter {

    /** @param stale upserts the version guard refused, because a newer version of the row was already stored */
    record Result(int inserted, int updated, int stale, int deleted, int awardsChanged) {
    }

    private static final String UPSERT_TRANSACTION = """
            INSERT INTO award_transaction (
                transaction_id, award_id, modification_number, action_date, action_type_code,
                federal_action_obligation, description, source_modified_at, source_file_date, source_file, piid,
                parent_piid, award_type, award_description, awarding_toptier_code, awarding_subtier_code,
                funding_toptier_code, naics_code, naics_description, psc_code, psc_description, pop_state_code,
                pop_country_code, total_obligated, potential_total_value, pop_start_date, pop_end_date,
                recipient_uei, recipient_name, recipient_parent_uei, recipient_parent_name, recipient_city,
                recipient_state_code, recipient_country_code)
            VALUES (
                :transactionId, :awardId, :modificationNumber, :actionDate, :actionTypeCode,
                :federalActionObligation, :transactionDescription, :sourceModifiedAt, :sourceFileDate, :sourceFile,
                :piid, :parentPiid, :awardType, :awardDescription, :awardingToptierCode, :awardingSubtierCode,
                :fundingToptierCode, :naicsCode, :naicsDescription, :pscCode, :pscDescription, :popStateCode,
                :popCountryCode, :totalObligated, :potentialTotalValue, :popStartDate, :popEndDate,
                :recipientUei, :recipientName, :recipientParentUei, :recipientParentName, :recipientCity,
                :recipientStateCode, :recipientCountryCode)
            ON CONFLICT (transaction_id) DO UPDATE SET
                award_id = EXCLUDED.award_id, modification_number = EXCLUDED.modification_number,
                action_date = EXCLUDED.action_date, action_type_code = EXCLUDED.action_type_code,
                federal_action_obligation = EXCLUDED.federal_action_obligation, description = EXCLUDED.description,
                source_modified_at = EXCLUDED.source_modified_at, source_file_date = EXCLUDED.source_file_date,
                source_file = EXCLUDED.source_file, piid = EXCLUDED.piid, parent_piid = EXCLUDED.parent_piid,
                award_type = EXCLUDED.award_type, award_description = EXCLUDED.award_description,
                awarding_toptier_code = EXCLUDED.awarding_toptier_code,
                awarding_subtier_code = EXCLUDED.awarding_subtier_code,
                funding_toptier_code = EXCLUDED.funding_toptier_code, naics_code = EXCLUDED.naics_code,
                naics_description = EXCLUDED.naics_description, psc_code = EXCLUDED.psc_code,
                psc_description = EXCLUDED.psc_description, pop_state_code = EXCLUDED.pop_state_code,
                pop_country_code = EXCLUDED.pop_country_code, total_obligated = EXCLUDED.total_obligated,
                potential_total_value = EXCLUDED.potential_total_value, pop_start_date = EXCLUDED.pop_start_date,
                pop_end_date = EXCLUDED.pop_end_date, recipient_uei = EXCLUDED.recipient_uei,
                recipient_name = EXCLUDED.recipient_name, recipient_parent_uei = EXCLUDED.recipient_parent_uei,
                recipient_parent_name = EXCLUDED.recipient_parent_name, recipient_city = EXCLUDED.recipient_city,
                recipient_state_code = EXCLUDED.recipient_state_code,
                recipient_country_code = EXCLUDED.recipient_country_code,
                deleted_at = NULL
            WHERE (award_transaction.source_file_date, award_transaction.source_file)
                < (EXCLUDED.source_file_date, EXCLUDED.source_file)
            """;

    // A delete for a transaction that was never stored matches nothing, and is ignored.
    private static final String DELETE_TRANSACTION = """
            UPDATE award_transaction
            SET deleted_at = now(), source_file_date = :sourceFileDate, source_file = :sourceFile
            WHERE transaction_id = :transactionId
              AND (source_file_date, source_file) < (:sourceFileDate, :sourceFile)
            """;

    private static final String INSERT_AGENCY = """
            INSERT INTO agency (code, level, name, parent_code) VALUES (:code, :level, :name, :parentCode)
            ON CONFLICT (code) DO NOTHING
            """;

    // The latest live transaction supplies the recipient; one with only deleted transactions keeps its latest.
    private static final String PROJECT_RECIPIENTS = """
            INSERT INTO recipient (uei, name, parent_uei, parent_name, city, state_code, country_code)
            SELECT DISTINCT ON (recipient_uei)
                   recipient_uei, recipient_name, recipient_parent_uei, recipient_parent_name, recipient_city,
                   recipient_state_code, recipient_country_code
            FROM award_transaction
            WHERE recipient_uei IN (SELECT recipient_uei FROM award_transaction WHERE award_id IN (:awardIds))
            ORDER BY recipient_uei, deleted_at IS NULL DESC, action_date DESC, modification_number DESC,
                     transaction_id DESC
            ON CONFLICT (uei) DO UPDATE SET
                name = EXCLUDED.name, parent_uei = EXCLUDED.parent_uei, parent_name = EXCLUDED.parent_name,
                city = EXCLUDED.city, state_code = EXCLUDED.state_code, country_code = EXCLUDED.country_code
            WHERE (recipient.name, recipient.parent_uei, recipient.parent_name, recipient.city,
                   recipient.state_code, recipient.country_code)
                IS DISTINCT FROM (EXCLUDED.name, EXCLUDED.parent_uei, EXCLUDED.parent_name, EXCLUDED.city,
                                  EXCLUDED.state_code, EXCLUDED.country_code)
            """;

    // Award-level fields come from the latest live transaction in action order; counts and dates from all live
    // ones. An award whose transactions are all deleted is kept, marked deleted, so the index can drop it.
    private static final String PROJECT_AWARDS = """
            WITH latest AS (
                SELECT DISTINCT ON (award_id) *
                FROM award_transaction
                WHERE award_id IN (:awardIds)
                ORDER BY award_id, deleted_at IS NULL DESC, action_date DESC, modification_number DESC,
                         transaction_id DESC
            ),
            live AS (
                SELECT award_id, count(*) AS transaction_count, min(action_date) AS first_action_date,
                       max(action_date) AS last_action_date, max(source_modified_at) AS source_modified_at
                FROM award_transaction
                WHERE award_id IN (:awardIds) AND deleted_at IS NULL
                GROUP BY award_id
            ),
            changed AS (
                INSERT INTO award (
                    award_id, piid, parent_piid, award_type, description, description_hash, awarding_toptier_code,
                    awarding_subtier_code, funding_toptier_code, recipient_uei, naics_code, naics_description,
                    psc_code, psc_description, pop_state_code, pop_country_code, total_obligated,
                    potential_total_value, pop_start_date, pop_end_date, first_action_date, last_action_date,
                    source_modified_at, index_version, transaction_count, deleted_at)
                SELECT t.award_id, t.piid, t.parent_piid, t.award_type, t.award_description,
                       encode(sha256(convert_to(upper(regexp_replace(btrim(t.award_description), '\\s+', ' ', 'g')),
                                                'UTF8')), 'hex'),
                       t.awarding_toptier_code, t.awarding_subtier_code, t.funding_toptier_code, t.recipient_uei,
                       t.naics_code, t.naics_description, t.psc_code, t.psc_description, t.pop_state_code,
                       t.pop_country_code, t.total_obligated, t.potential_total_value, t.pop_start_date,
                       t.pop_end_date, coalesce(l.first_action_date, t.action_date),
                       coalesce(l.last_action_date, t.action_date),
                       coalesce(l.source_modified_at, t.source_modified_at), 1, coalesce(l.transaction_count, 0),
                       CASE WHEN l.award_id IS NULL THEN now() END
                FROM latest t LEFT JOIN live l ON l.award_id = t.award_id
                ON CONFLICT (award_id) DO UPDATE SET
                    piid = EXCLUDED.piid, parent_piid = EXCLUDED.parent_piid, award_type = EXCLUDED.award_type,
                    description = EXCLUDED.description, description_hash = EXCLUDED.description_hash,
                    awarding_toptier_code = EXCLUDED.awarding_toptier_code,
                    awarding_subtier_code = EXCLUDED.awarding_subtier_code,
                    funding_toptier_code = EXCLUDED.funding_toptier_code, recipient_uei = EXCLUDED.recipient_uei,
                    naics_code = EXCLUDED.naics_code, naics_description = EXCLUDED.naics_description,
                    psc_code = EXCLUDED.psc_code, psc_description = EXCLUDED.psc_description,
                    pop_state_code = EXCLUDED.pop_state_code, pop_country_code = EXCLUDED.pop_country_code,
                    total_obligated = EXCLUDED.total_obligated,
                    potential_total_value = EXCLUDED.potential_total_value,
                    pop_start_date = EXCLUDED.pop_start_date, pop_end_date = EXCLUDED.pop_end_date,
                    first_action_date = EXCLUDED.first_action_date, last_action_date = EXCLUDED.last_action_date,
                    source_modified_at = EXCLUDED.source_modified_at,
                    transaction_count = EXCLUDED.transaction_count,
                    deleted_at = CASE WHEN EXCLUDED.deleted_at IS NULL THEN NULL
                                      ELSE coalesce(award.deleted_at, EXCLUDED.deleted_at) END,
                    index_version = award.index_version + 1,
                    updated_at = now()
                WHERE (award.piid, award.parent_piid, award.award_type, award.description,
                       award.awarding_toptier_code, award.awarding_subtier_code, award.funding_toptier_code,
                       award.recipient_uei, award.naics_code, award.naics_description, award.psc_code,
                       award.psc_description, award.pop_state_code, award.pop_country_code, award.total_obligated,
                       award.potential_total_value, award.pop_start_date, award.pop_end_date,
                       award.first_action_date, award.last_action_date, award.source_modified_at,
                       award.transaction_count, award.deleted_at IS NULL)
                    IS DISTINCT FROM
                      (EXCLUDED.piid, EXCLUDED.parent_piid, EXCLUDED.award_type, EXCLUDED.description,
                       EXCLUDED.awarding_toptier_code, EXCLUDED.awarding_subtier_code, EXCLUDED.funding_toptier_code,
                       EXCLUDED.recipient_uei, EXCLUDED.naics_code, EXCLUDED.naics_description, EXCLUDED.psc_code,
                       EXCLUDED.psc_description, EXCLUDED.pop_state_code, EXCLUDED.pop_country_code,
                       EXCLUDED.total_obligated, EXCLUDED.potential_total_value, EXCLUDED.pop_start_date,
                       EXCLUDED.pop_end_date, EXCLUDED.first_action_date, EXCLUDED.last_action_date,
                       EXCLUDED.source_modified_at, EXCLUDED.transaction_count, EXCLUDED.deleted_at IS NULL)
                RETURNING award_id, index_version
            )
            INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
            SELECT award_id, 'AwardChanged', 'TRANSACTION', index_version FROM changed
            """;

    private final NamedParameterJdbcTemplate jdbc;

    TransactionWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    Result write(List<ContractTransactionIngested> ingested, List<ContractTransactionDeleted> deleted) {
        Set<String> awardIds = new LinkedHashSet<>();
        ingested.forEach(event -> awardIds.add(event.awardId()));
        deleted.forEach(event -> awardIds.add(event.awardId()));
        if (awardIds.isEmpty()) {
            return new Result(0, 0, 0, 0, 0);
        }

        jdbc.batchUpdate(INSERT_AGENCY, agencies(ingested));
        // An upsert reports only whether it applied, so new rows are told from updated ones by counting the
        // batch's transactions before and after.
        var transactionIds = new MapSqlParameterSource("ids",
                ingested.stream().map(ContractTransactionIngested::transactionId).toList());
        long storedBefore = ingested.isEmpty() ? 0 : storedTransactions(transactionIds);
        // Upserts run before deletes, so a delete in the same batch as its transaction still applies. The source
        // file version decides every conflict, so the order of events within the batch doesn't matter.
        int[] upserts = jdbc.batchUpdate(UPSERT_TRANSACTION, ingested.stream().map(EventParameters::of)
                .toArray(SqlParameterSource[]::new));
        int[] deletes = jdbc.batchUpdate(DELETE_TRANSACTION, deleted.stream().map(EventParameters::of)
                .toArray(SqlParameterSource[]::new));
        var ids = new MapSqlParameterSource("awardIds", awardIds);
        jdbc.update(PROJECT_RECIPIENTS, ids);
        int awardsChanged = jdbc.update(PROJECT_AWARDS, ids);

        int applied = Arrays.stream(upserts).sum();
        int inserted = ingested.isEmpty() ? 0 : (int) (storedTransactions(transactionIds) - storedBefore);
        return new Result(inserted, applied - inserted, upserts.length - applied, Arrays.stream(deletes).sum(),
                awardsChanged);
    }

    private long storedTransactions(MapSqlParameterSource transactionIds) {
        return jdbc.queryForObject("SELECT count(*) FROM award_transaction WHERE transaction_id IN (:ids)",
                transactionIds, Long.class);
    }

    /** Toptier agencies first, so each subtier agency's parent exists when it is inserted. */
    private static SqlParameterSource[] agencies(List<ContractTransactionIngested> ingested) {
        Map<String, MapSqlParameterSource> agencies = new LinkedHashMap<>();
        Stream.concat(
                ingested.stream().flatMap(event -> Stream.of(
                        agency(event.awardingToptierCode(), "toptier", event.awardingToptierName(), null),
                        agency(event.fundingToptierCode(), "toptier", event.fundingToptierName(), null))),
                ingested.stream().map(event -> agency(event.awardingSubtierCode(), "subtier",
                        event.awardingSubtierName(), event.awardingToptierCode())))
                .filter(agency -> agency.getValue("code") != null)
                .forEach(agency -> agencies.putIfAbsent((String) agency.getValue("code"), agency));
        return agencies.values().toArray(SqlParameterSource[]::new);
    }

    private static MapSqlParameterSource agency(String code, String level, String name, String parentCode) {
        return new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("level", level)
                .addValue("name", name == null ? code : name)
                .addValue("parentCode", parentCode);
    }
}
