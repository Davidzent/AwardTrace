package com.zntsns.awardtrace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/**
 * Each runtime role loads its own beans and no other role's (doc 13): a role started alone can't consume another
 * role's topics or serve HTTP. Each nested class repeats an existing integration test's setup, so the role's context
 * and its containers are reused rather than started again.
 */
class ProfileIsolationIT {

    /** A few beans only their role may load. They're internal to their modules, so they're named, not typed. */
    private static final Map<String, List<String>> ROLE_BEANS = Map.of(
            "ingest", List.of("ingestRuns", "sourceFileStore", "storedFilePublisher", "subawardDownloads"),
            "pipeline", List.of("transactionListener", "subawardListener", "outboxRelay"),
            "indexer", List.of("awardIndexer", "awardsIndex"),
            "enricher", List.of("classificationStore", "groupClassifier", "anthropicClient"),
            "api", List.of("searchController", "awardController", "recipientController", "rateLimiting"));

    @Nested
    @SpringBootTest
    @ActiveProfiles("ingest")
    @Import(TestcontainersConfiguration.class)
    class Ingest {

        @Test
        void loadsOnlyIngestBeans(@Autowired ApplicationContext context) {
            assertLoadsOnly("ingest", context);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("pipeline")
    @Import(TestcontainersConfiguration.class)
    class Pipeline {

        @Test
        void loadsOnlyPipelineBeans(@Autowired ApplicationContext context) {
            assertLoadsOnly("pipeline", context);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("indexer")
    @Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
    class Indexer {

        @Test
        void loadsOnlyIndexerBeans(@Autowired ApplicationContext context) {
            assertLoadsOnly("indexer", context);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("enricher")
    @Import(TestcontainersConfiguration.class)
    class Enricher {

        @Test
        void loadsOnlyEnricherBeans(@Autowired ApplicationContext context) {
            assertLoadsOnly("enricher", context);
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("api")
    @Import(TestcontainersConfiguration.class)
    class Api {

        @Test
        void loadsOnlyApiBeans(@Autowired ApplicationContext context) {
            assertLoadsOnly("api", context);
        }
    }

    /** Only the api role is a web application; the others never bind a port. */
    private static void assertLoadsOnly(String role, ApplicationContext context) {
        ROLE_BEANS.forEach((owner, beans) -> beans.forEach(bean -> assertThat(context.containsBean(bean))
                .as("%s under the %s role", bean, role)
                .isEqualTo(owner.equals(role))));
        assertThat(context instanceof WebApplicationContext).as("a web application").isEqualTo(role.equals("api"));
    }
}
