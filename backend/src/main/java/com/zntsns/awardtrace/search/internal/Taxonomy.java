package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.AwardQueries;
import com.zntsns.awardtrace.award.Category;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The category taxonomy, read once at startup. Only a migration changes it, and a migration ships with a new
 * deployment. The API already needs PostgreSQL to start, so search gains no new dependency on it at request time.
 */
@Component
@Profile("api")
class Taxonomy {

    private final List<Category> categories;
    private final Map<String, String> labels;

    Taxonomy(AwardQueries awards) {
        categories = awards.categories();
        labels = categories.stream().collect(Collectors.toMap(Category::code, Category::label));
    }

    List<Category> categories() {
        return categories;
    }

    /** Null for a code this deployment's taxonomy doesn't have. */
    String label(String code) {
        return labels.get(code);
    }

    /** Relabels a facet whose documents carry only codes. */
    List<SearchResults.FacetValue> labeled(List<SearchResults.FacetValue> values) {
        return values.stream()
                .map(value -> new SearchResults.FacetValue(value.value(), label(value.value()), value.count()))
                .toList();
    }
}
