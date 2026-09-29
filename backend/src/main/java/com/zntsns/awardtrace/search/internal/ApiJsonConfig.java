package com.zntsns.awardtrace.search.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("api")
class ApiJsonConfig {

    /**
     * Money leaves the API as a decimal string, such as {@code "4812000.00"}, so JavaScript never rounds it. Every
     * money column is numeric(18,2), so the string always has two decimal places.
     */
    @Bean
    JsonMapperBuilderCustomizer moneyAsDecimalStrings() {
        return builder -> builder.withConfigOverride(BigDecimal.class,
                override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
    }
}
