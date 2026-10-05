package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Item;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Reply;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Stop;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A stand-in for the Messages API, so the real SDK builds and sends each request without reaching Anthropic. */
class AnthropicClassificationModelTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String ANSWER = "{\"items\": [{\"id\": \"aaaaaaaaaaaa\", \"category\": \"OTHER\", "
            + "\"confidence\": 0.9}]}";
    private static final String USAGE = "{\"input_tokens\": 900, \"output_tokens\": 60}";
    private static final List<Item> ITEMS = List.of(new Item("aaaaaaaaaaaa", "JANITORIAL SERVICES"),
            new Item("bbbbbbbbbbbb", "SIGNS READING \"KEEP OUT\"\nAND POSTS"));

    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> response = new AtomicReference<>();
    private final HttpServer api;
    private final AnthropicClient client;

    AnthropicClassificationModelTest() throws IOException {
        api = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        api.createContext("/v1/messages", this::messages);
        api.start();
        client = AnthropicOkHttpClient.builder()
                .baseUrl("http://localhost:" + api.getAddress().getPort())
                .apiKey("test-key")
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void stop() {
        client.close();
        api.stop(0);
    }

    @Test
    void sendsHaikuTheFrozenPromptWithoutEffortOrThinking() throws IOException {
        response.set(message("end_turn", USAGE, text(ANSWER)));

        Reply reply = model("claude-haiku-4-5").classify(ITEMS);

        assertThat(reply).isEqualTo(new Reply(Stop.COMPLETE, ANSWER, new Usage(900, 60, 0, 0)));
        JsonNode body = JSON.readTree(request.get());
        assertThat(body.path("model").asString()).isEqualTo("claude-haiku-4-5");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(AnthropicClassificationModel.MAX_TOKENS);
        assertThat(body.path("system").path(0).path("text").asString()).isEqualTo(prompt());
        assertThat(body.path("system").path(0).path("cache_control").path("type").asString()).isEqualTo("ephemeral");
        assertThat(body.path("messages").path(0).path("role").asString()).isEqualTo("user");
        assertThat(body.path("messages").path(0).path("content").asString()).isEqualTo("""
                1. {"id":"aaaaaaaaaaaa","text":"JANITORIAL SERVICES"}
                2. {"id":"bbbbbbbbbbbb","text":"SIGNS READING \\"KEEP OUT\\"\\nAND POSTS"}""");
        assertThat(body.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(body.path("output_config").path("format").path("schema"))
                .isEqualTo(JSON.valueToTree(ClassificationValidation.SCHEMA));
        assertThat(body.path("output_config").has("effort")).isFalse();
        assertThat(body.has("thinking")).isFalse();
        assertThat(request.get()).doesNotContain("fallback");
    }

    @Test
    void setsOpusEffortToLowAndReadsOnlyTheAnswerAfterItsThinking() throws IOException {
        response.set(message("end_turn", """
                {"input_tokens": 300, "output_tokens": 410, "cache_creation_input_tokens": 600,
                 "cache_read_input_tokens": 0}""",
                "{\"type\": \"thinking\", \"thinking\": \"\", \"signature\": \"c2ln\"}", text(ANSWER)));

        Reply reply = model("claude-opus-5-5").classify(ITEMS);

        assertThat(reply).isEqualTo(new Reply(Stop.COMPLETE, ANSWER, new Usage(300, 410, 600, 0)));
        JsonNode body = JSON.readTree(request.get());
        assertThat(body.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(body.has("thinking")).isFalse();
        assertThat(request.get()).doesNotContain("fallback");
    }

    @Test
    void reportsARefusal() {
        response.set("""
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-opus-5-5", "content": [],
                 "stop_reason": "refusal", "stop_sequence": null,
                 "stop_details": {"type": "refusal", "category": "cyber", "explanation": null},
                 "usage": {"input_tokens": 900, "output_tokens": 0}}""");

        assertThat(model("claude-opus-5-5").classify(ITEMS))
                .isEqualTo(new Reply(Stop.REFUSAL, null, new Usage(900, 0, 0, 0)));
    }

    @Test
    void reportsATruncatedAnswer() {
        response.set(message("max_tokens", USAGE, text("{\"items\": [{\"id\": \"aaaa")));

        assertThat(model("claude-haiku-4-5").classify(ITEMS))
                .isEqualTo(new Reply(Stop.MAX_TOKENS, null, new Usage(900, 60, 0, 0)));
    }

    @Test
    void boundsARequestsCostByEveryOutputTokenItMayUse() {
        BigDecimal haiku = model("claude-haiku-4-5").maxCost(ITEMS);

        // 4,096 output tokens at $5 a million is $0.02048; the input adds a little.
        assertThat(haiku).isGreaterThan(new BigDecimal("0.02048")).isLessThan(new BigDecimal("0.03"));
        // Every Opus 5.5 price is four times Haiku 4.5's.
        assertThat(model("claude-opus-5-5").maxCost(ITEMS)).isEqualByComparingTo(haiku.multiply(BigDecimal.valueOf(4)));
    }

    @Test
    void refusesAModelWhoseSettingsAreUnknown() {
        assertThatThrownBy(() -> model("claude-sonnet-5-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claude-sonnet-5-5");
    }

    @Test
    void theFirstPromptListsExactlyTheSchemasCategories() throws IOException {
        var rows = Pattern.compile("^\\| ([A-Z_]+) \\| ", Pattern.MULTILINE).matcher(prompt());

        assertThat(rows.results().map(row -> row.group(1)).toList())
                .isEqualTo(ClassificationValidation.CATEGORIES);
    }

    private AnthropicClassificationModel model(String model) {
        return new AnthropicClassificationModel(client, new EnrichmentProperties(model, "v1", BigDecimal.ONE));
    }

    private void messages(HttpExchange exchange) throws IOException {
        request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** A Messages API response with this usage and these content blocks. */
    private static String message(String stopReason, String usage, String... blocks) {
        return """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-test",
                 "content": [%s], "stop_reason": "%s", "stop_sequence": null, "usage": %s}"""
                .formatted(String.join(", ", blocks), stopReason, usage);
    }

    private static String text(String text) {
        return "{\"type\": \"text\", \"text\": " + JSON.writeValueAsString(text) + "}";
    }

    private static String prompt() throws IOException {
        try (InputStream in = AnthropicClassificationModelTest.class.getResourceAsStream("/prompts/classify-v1.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
