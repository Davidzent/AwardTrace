package com.zntsns.awardtrace.search.internal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.BindParam;

/** The OpenAPI document served at {@code /api/v1/openapi.json} and committed as {@code backend/openapi.json}. */
@Configuration(proxyBeanMethods = false)
@Profile("api")
class OpenApiConfig {

    /**
     * springdoc reads models through Jackson 2, which doesn't see the API's Jackson 3 settings, so this repeats the two
     * that shape the wire format: snake_case names, and money as decimal strings ({@link ApiJsonConfig}).
     */
    @Bean
    ModelResolver apiModels(ObjectMapperProvider mappers) {
        SpringDocUtils.getConfig().replaceWithSchema(BigDecimal.class,
                new StringSchema().format("decimal").example("4812000.00"));
        return new ModelResolver(mappers.jsonMapper().copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE))
                .openapi31(mappers.isOpenapi31());
    }

    /** Names query parameters as Spring binds them, such as {@code fiscal_year}; springdoc ignores @BindParam. */
    @Bean
    ParameterCustomizer bindParamNames() {
        return (parameter, methodParameter) -> {
            var bindParam = methodParameter.getParameterAnnotation(BindParam.class);
            if (parameter != null && bindParam != null) {
                parameter.setName(bindParam.value());
            }
            return parameter;
        };
    }

    @Bean
    OpenAPI awardTraceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AwardTrace API")
                        .version("v1")
                        .description("Read-only search over federal contract awards published by USAspending.gov."))
                // Relative, so the document reads the same whichever host serves it.
                .servers(List.of(new Server().url("/")));
    }

    /** Every error is RFC 9457 problem details (doc 07), so every operation declares that as its default response. */
    @Bean
    OpenApiCustomizer problemResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas("Problem", problem());
            var response = new ApiResponse()
                    .description("Problem details")
                    .content(new Content().addMediaType("application/problem+json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem"))));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().addApiResponse("default", response)));
        };
    }

    private static Schema<?> problem() {
        var fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        return new ObjectSchema()
                .addProperty("type", new StringSchema().description("Stable, such as award-not-found"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty("errors", new ArraySchema().items(fieldError)
                        .description("One per invalid parameter, on invalid-search-parameters"));
    }
}
