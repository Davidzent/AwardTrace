package com.zntsns.awardtrace.search.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.award.AwardQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Elasticsearch points at a closed port, so every search fails to connect, as it does when the node is down. */
@SpringBootTest(properties = "spring.elasticsearch.uris=http://127.0.0.1:1")
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class ApiErrorsIT {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AwardQueries awards;

    @Test
    void answersServiceUnavailableWhileElasticsearchIsUnreachable() {
        var result = mvc.get().uri("/api/v1/awards/search?q=fire").exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).headers().hasValue(HttpHeaders.RETRY_AFTER, "30");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("search-unavailable");
    }

    @Test
    void describesAnUnexpectedFailureWithoutItsDetails() {
        when(awards.find(anyString(), anyInt())).thenThrow(new IllegalStateException("connection pool exhausted"));

        var result = mvc.get().uri("/api/v1/awards/{awardId}", "CONT_AWD_ANY").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("internal-error");
        assertThat(result).body().asString().doesNotContain("connection pool");
    }

    @Test
    void keepsTheFrameworkOwnErrorsAsProblemDetails() {
        var result = mvc.post().uri("/api/v1/awards/search").exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
