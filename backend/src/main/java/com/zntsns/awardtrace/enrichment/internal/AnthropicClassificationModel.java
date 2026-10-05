package com.zntsns.awardtrace.enrichment.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Classifies a group of descriptions with one Messages API request (doc 09). The system prompt is
 * {@code prompts/classify-{version}.txt}, sent byte for byte; the user message numbers the items, each a JSON object
 * of its {@code id} and {@code text}; and structured output holds the answer to
 * {@link ClassificationValidation#SCHEMA}.
 *
 * <p>Each model gets only the settings it accepts. Haiku 4.5 rejects {@code effort} and thinks only when asked, so
 * its requests set neither. Opus 5.5 always thinks and can't be told not to, so its requests leave thinking unset too;
 * effort is its only control, set to low. No request sets server-side fallbacks: a refusal stays a refusal, so the
 * ceiling check scores Opus's own answers and counts its refusals.
 */
@Component
@Profile("enricher")
class AnthropicClassificationModel implements ClassificationModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClassificationModel.class);

    /** About 30 tokens an item, with room for Opus 5.5's thinking. A truncated answer splits its group. */
    static final long MAX_TOKENS = 4096;

    private static final JsonMapper JSON = JsonMapper.shared();

    private final AnthropicClient client;
    private final String model;
    private final String promptVersion;
    private final Prices prices;
    private final List<TextBlockParam> system;
    private final OutputConfig outputConfig;
    /** The characters every request sends besides its items: the system prompt and the output schema. */
    private final int fixedChars;

    AnthropicClassificationModel(AnthropicClient client, EnrichmentProperties properties) {
        this.client = client;
        this.model = properties.model();
        this.promptVersion = properties.promptVersion();
        this.prices = Prices.of(model);
        String prompt = prompt(promptVersion);
        // Caches only where the prompt reaches the model's minimum: 512 tokens on Opus 5.5, 4,096 on Haiku 4.5.
        this.system = List.of(TextBlockParam.builder()
                .text(prompt)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build());
        this.fixedChars = prompt.length() + JSON.writeValueAsString(ClassificationValidation.SCHEMA).length();
        var schema = JsonOutputFormat.Schema.builder();
        ClassificationValidation.SCHEMA.forEach((key, value) ->
                schema.putAdditionalProperty(key, JsonValue.from(value)));
        var output = OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema.build()).build());
        effort(model).ifPresent(output::effort);
        this.outputConfig = output.build();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String promptVersion() {
        return promptVersion;
    }

    /**
     * Every output token the request may use, plus its input at 2 characters a token, which overcounts, priced as a
     * cache write, which costs more than plain input.
     */
    @Override
    public BigDecimal maxCost(List<Item> items) {
        long inputTokens = (fixedChars + userMessage(items).length()) / 2;
        return prices.cost(new Usage(0, MAX_TOKENS, inputTokens, 0));
    }

    @Override
    public Reply classify(List<Item> items) {
        Message message = client.messages().create(MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(system)
                .addUserMessage(userMessage(items))
                .outputConfig(outputConfig)
                .build());
        var used = message.usage();
        var usage = new Usage(used.inputTokens(), used.outputTokens(), used.cacheCreationInputTokens().orElse(0L),
                used.cacheReadInputTokens().orElse(0L));
        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            message.stopDetails().ifPresent(details -> log.warn("Claude refused {} descriptions: {} {}", items.size(),
                    details.category().map(Object::toString).orElse("-"), details.explanation().orElse("")));
            return new Reply(Stop.REFUSAL, null, usage);
        }
        // Either way the answer was cut off before it closed.
        if (StopReason.MAX_TOKENS.equals(stop) || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stop)) {
            return new Reply(Stop.MAX_TOKENS, null, usage);
        }
        // Opus 5.5's thinking arrives as its own blocks, ahead of the answer's text.
        String answer = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return new Reply(Stop.COMPLETE, answer, usage);
    }

    /** {@code 1. {"id":"…","text":"…"}}, a line an item. JSON keeps a text's quotes and line breaks unambiguous. */
    static String userMessage(List<Item> items) {
        return IntStream.range(0, items.size())
                .mapToObj(i -> (i + 1) + ". " + JSON.writeValueAsString(items.get(i)))
                .collect(Collectors.joining("\n"));
    }

    /** The effort each known model takes, if any. Another model needs its settings checked and added here first. */
    private static Optional<OutputConfig.Effort> effort(String model) {
        return switch (model) {
            case "claude-haiku-4-5" -> Optional.empty();
            case "claude-opus-5-5" -> Optional.of(OutputConfig.Effort.LOW);
            case null, default -> throw new IllegalArgumentException("No request settings for model " + model);
        };
    }

    private static String prompt(String version) {
        var resource = new ClassPathResource("prompts/classify-" + version + ".txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No prompt " + resource.getPath(), e);
        }
    }
}
