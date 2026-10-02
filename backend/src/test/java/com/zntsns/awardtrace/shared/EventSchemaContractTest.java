package com.zntsns.awardtrace.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.ingest.SubawardReported;
import com.zntsns.awardtrace.outbox.AwardChanged;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Every event type, as {@link EventCodec} writes it, validates against its committed JSON Schema under
 * {@code src/main/resources/events/} (doc 05, ADR 0010).
 */
class EventSchemaContractTest {

    private static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final EventEnvelope.Source SOURCE = new EventEnvelope.Source(
            UUID.fromString("01926f39-2b10-7d3e-8a51-0c6f4e9b7d12"), "raw/contracts/2026/9f2c4e7a.zip", 18412);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 14);
    private static final Instant MODIFIED = Instant.parse("2026-09-20T03:11:48Z");

    /** One of every event type with every field set, so each property in its schema is exercised. */
    static Stream<EventEnvelope<?>> everyEventType() {
        return Stream.of(
                EventEnvelope.of(new ContractTransactionIngested("TXN_P00003", "AWARD", "FY2026_012_Contracts_Full.csv",
                        DATE, MODIFIED, "12024B26C0001", "12024B24T7051", "P00003", "C", "B", DATE,
                        new BigDecimal("-12500.00"), new BigDecimal("4812000.00"), new BigDecimal("9650000.00"),
                        DATE, DATE.plusYears(1), "012", "Department of Agriculture", "12C2", "Forest Service", "012",
                        "Department of Agriculture", "MN5KRX2W9R46", "NOMADIC LAND CAMPS, LLC", "PPPPPPPPPPP1",
                        "NOMADIC HOLDINGS INC", "BOISE", "ID", "USA", "ID", "USA", "541512",
                        "COMPUTER SYSTEMS DESIGN SERVICES", "DA01", "IT AND TELECOM", "Descope task 4", "Camps"),
                        SOURCE),
                EventEnvelope.of(new ContractTransactionDeleted("TXN_P00004", "AWARD", "FY(All)_012_Delta.csv", DATE),
                        SOURCE),
                EventEnvelope.of(new SubawardReported("SAM-REPORT-1", MODIFIED, "AWARD", "MN5KRX2W9R46",
                        "NOMADIC LAND CAMPS, LLC", "E2QCEKQXLN48", "DVORAK, LLC", "SUB-001", new BigDecimal("-200.50"),
                        DATE, "Catering"), SOURCE),
                EventEnvelope.of(new AwardChanged("AWARD", 3, "SUBAWARD"), null));
    }

    @ParameterizedTest
    @MethodSource("everyEventType")
    void validatesAgainstItsCommittedSchema(EventEnvelope<?> envelope) {
        String json = EventCodec.write(envelope);
        Schema schema = schemaFor(envelope.eventType());

        assertThat(errors(schema, json)).isEmpty();
        // With every field set, the payload has exactly the properties the schema describes: no stale ones.
        assertThat(JsonMapper.shared().readTree(json).get("payload").propertyNames())
                .containsExactlyInAnyOrderElementsOf(schemaJson(envelope.eventType())
                        .at("/properties/payload/properties").propertyNames());
    }

    @Test
    void hasASchemaForEveryEventTypeAndNoOther() throws IOException, URISyntaxException {
        Path folder = Path.of(EventSchemaContractTest.class.getResource("/events").toURI());
        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files.map(file -> file.getFileName().toString()).collect(Collectors.toSet()))
                    .isEqualTo(everyEventType().map(envelope -> envelope.eventType() + ".v1.json")
                            .collect(Collectors.toSet()));
        }
    }

    @Test
    void rejectsEventsThatBreakTheContract() {
        Schema schema = schemaFor("AwardChanged");
        String valid = EventCodec.write(EventEnvelope.of(new AwardChanged("AWARD", 3, "TRANSACTION"), null));

        for (Consumer<ObjectNode> breakIt : List.<Consumer<ObjectNode>>of(
                event -> event.put("occurred_at", "yesterday"),
                event -> event.put("schema_version", 2),
                event -> event.putObject("source"),
                event -> ((ObjectNode) event.get("payload")).put("index_version", "3"),
                event -> ((ObjectNode) event.get("payload")).put("change_reason", "UNKNOWN"),
                event -> ((ObjectNode) event.get("payload")).remove("award_id"))) {
            ObjectNode event = (ObjectNode) JsonMapper.shared().readTree(valid);
            breakIt.accept(event);
            assertThat(errors(schema, event.toString())).as(event.toString()).isNotEmpty();
        }
    }

    @Test
    void acceptsMoneyAsADecimalStringOnly() {
        Schema schema = schemaFor("SubawardReported");
        ObjectNode event = (ObjectNode) JsonMapper.shared().readTree(EventCodec.write(everyEventType()
                .filter(envelope -> envelope.payload() instanceof SubawardReported).findFirst().orElseThrow()));
        var payload = (ObjectNode) event.get("payload");

        payload.put("amount", -200.5);
        assertThat(errors(schema, event.toString())).isNotEmpty();
        payload.put("amount", "1E+3");
        assertThat(errors(schema, event.toString())).isNotEmpty();
    }

    /** Formats such as {@code date} and {@code uuid} are only annotations in 2020-12 unless asserted. */
    private static List<Error> errors(Schema schema, String json) {
        return schema.validate(json, InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
    }

    private static Schema schemaFor(String eventType) {
        return SCHEMAS.getSchema(schemaJson(eventType));
    }

    private static JsonNode schemaJson(String eventType) {
        try (InputStream in = EventSchemaContractTest.class.getResourceAsStream("/events/" + eventType + ".v1.json")) {
            assertThat(in).as("schema for " + eventType).isNotNull();
            return JsonMapper.shared().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
