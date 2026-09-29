package com.zntsns.awardtrace.indexer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("indexer")
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class AwardsIndexIT {

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    AwardsIndex awardsIndex;

    @Test
    void createsTheFirstIndexBehindTheAlias() throws Exception {
        assertThat(elasticsearch.indices().getAlias(request -> request.name(AwardsIndex.ALIAS)).aliases())
                .containsOnlyKeys(AwardsIndex.MAPPING);

        var settings = elasticsearch.indices().getSettings(request -> request.index(AwardsIndex.MAPPING))
                .get(AwardsIndex.MAPPING).settings().index();
        assertThat(settings.numberOfShards()).isEqualTo("1");
        assertThat(settings.numberOfReplicas()).isEqualTo("0");
    }

    @Test
    void leavesAnExistingAliasAlone() throws Exception {
        awardsIndex.afterPropertiesSet();

        assertThat(elasticsearch.indices().getAlias(request -> request.name(AwardsIndex.ALIAS)).aliases())
                .containsOnlyKeys(AwardsIndex.MAPPING);
    }

    @Test
    void rejectsADocumentWithAFieldTheMappingDoesNotDeclare() {
        assertThatThrownBy(() -> elasticsearch.index(request -> request
                .index(AwardsIndex.ALIAS)
                .id("CONT_AWD_UNMAPPED")
                .document(Map.of("award_id", "CONT_AWD_UNMAPPED", "surprise", "field"))))
                .isInstanceOf(ElasticsearchException.class)
                .hasMessageContaining("strict_dynamic_mapping_exception");
    }
}
