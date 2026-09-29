package com.zntsns.awardtrace.indexer.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.shared.SearchIndexes;
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

    static final String ALIAS = SearchIndexes.AWARDS;

    /** The current mapping, in {@code elasticsearch/<MAPPING>.json}. The first index takes its name. */
    static final String MAPPING = "awards-v1";

    private final ElasticsearchClient elasticsearch;

    AwardsIndex(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (!elasticsearch.indices().existsAlias(request -> request.name(ALIAS)).value()) {
            create(MAPPING, true);
        }
    }

    /** Creates an index from the current mapping, optionally already behind the alias. */
    void create(String index, boolean behindAlias) throws IOException {
        try (var mapping = AwardsIndex.class.getResourceAsStream("/elasticsearch/" + MAPPING + ".json")) {
            elasticsearch.indices().create(request -> {
                request.index(index).withJson(mapping);
                return behindAlias ? request.aliases(ALIAS, alias -> alias) : request;
            });
        }
    }
}
