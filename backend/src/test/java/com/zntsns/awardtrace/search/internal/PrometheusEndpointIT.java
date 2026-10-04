package com.zntsns.awardtrace.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The api role serves the process's metrics to Prometheus (doc 11). The same setup as the other api tests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class PrometheusEndpointIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void servesTheMetricsInPrometheusFormat() {
        // A request, so there is one to time.
        assertThat(mvc.get().uri("/api/v1/categories").exchange()).hasStatusOk();

        var result = mvc.get().uri("/actuator/prometheus").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("jvm_memory_used_bytes", "http_server_requests_seconds_count");
    }
}
