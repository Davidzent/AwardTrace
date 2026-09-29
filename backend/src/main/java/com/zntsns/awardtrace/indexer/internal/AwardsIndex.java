package com.zntsns.awardtrace.indexer.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes sure the {@code awards} alias exists before anything writes to it. Queries and writes go through the alias,
 * which points at a versioned index such as {@code awards-v1}, so a rebuild can swap indexes without downtime.
 * Runs while the context starts, before listeners do: a write to a missing alias would otherwise create a plain
 * index named {@code awards} with a guessed mapping.
 */
@Component
@Profile("indexer")
class AwardsIndex implements InitializingBean {

    static final String ALIAS = "awards";
    static final String FIRST_INDEX = "awards-v1";

    private final ElasticsearchClient elasticsearch;

    AwardsIndex(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (elasticsearch.indices().existsAlias(request -> request.name(ALIAS)).value()) {
            return;
        }
        try (var mapping = AwardsIndex.class.getResourceAsStream("/elasticsearch/" + FIRST_INDEX + ".json")) {
            elasticsearch.indices().create(request -> request
                    .index(FIRST_INDEX)
                    .withJson(mapping)
                    .aliases(ALIAS, alias -> alias));
        }
    }
}
