package com.zntsns.awardtrace.search.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.NamedValue;
import com.zntsns.awardtrace.search.internal.AwardDetail.AgencyRef;
import com.zntsns.awardtrace.search.internal.AwardDetail.RecipientRef;
import com.zntsns.awardtrace.search.internal.SearchParams.Sort;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Builds and runs the award search described in doc 06. */
@Component
@Profile("api")
class AwardSearch {

    private final ElasticsearchClient elasticsearch;

    AwardSearch(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    SearchResults search(SearchParams params) throws IOException {
        var response = elasticsearch.search(request -> request
                .index(SearchIndexes.AWARDS)
                .query(query(params))
                .sort(sort(params.effectiveSort()))
                .from((params.page() - 1) * params.size())
                .size(params.size())
                .trackTotalHits(total -> total.enabled(true))
                .highlight(highlight -> highlight
                        .encoder(HighlighterEncoder.Html)
                        .preTags("<mark>")
                        .postTags("</mark>")
                        .fields(NamedValue.of("description", HighlightField.of(field -> field
                                .numberOfFragments(1)
                                .fragmentSize(160)))))
                .aggregations("total_obligated", aggregation -> aggregation.sum(sum -> sum.field("total_obligated"))),
                Map.class);

        var total = response.hits().total();
        // A sum over scaled_float values comes back as a double, so it is rounded to cents here.
        var totalObligated = BigDecimal.valueOf(response.aggregations().get("total_obligated").sum().value())
                .setScale(2, RoundingMode.HALF_UP);
        List<SearchResults.Result> results = response.hits().hits().stream()
                .map(hit -> result((Hit<Map<String, Object>>) (Hit) hit))
                .toList();
        return new SearchResults(total.value(), total.relation() == TotalHitsRelation.Gte, totalObligated,
                response.took(), params.page(), params.size(), results);
    }

    /**
     * A keyword matches recipient names first, then descriptions, then NAICS descriptions; a pasted contract number
     * outranks them all. Filters narrow without scoring.
     */
    private static Query query(SearchParams params) {
        var filters = new ArrayList<Query>();
        terms(filters, "awarding_toptier_code", params.agency());
        terms(filters, "category", params.category());
        terms(filters, "pop_state_code", params.state());
        terms(filters, "naics_code", params.naics());
        terms(filters, "fiscal_year", params.fiscalYear().stream().map(String::valueOf).toList());
        if (params.categorySource() != null) {
            terms(filters, "category_source", List.of(params.categorySource()));
        }
        if (params.minAmount() != null || params.maxAmount() != null) {
            filters.add(Query.of(query -> query.range(range -> range.number(number -> {
                number.field("total_obligated");
                if (params.minAmount() != null) {
                    number.gte(params.minAmount().doubleValue());
                }
                if (params.maxAmount() != null) {
                    number.lte(params.maxAmount().doubleValue());
                }
                return number;
            }))));
        }
        if (params.from() != null || params.to() != null) {
            filters.add(Query.of(query -> query.range(range -> range.date(date -> {
                date.field("last_action_date");
                if (params.from() != null) {
                    date.gte(params.from().toString());
                }
                if (params.to() != null) {
                    date.lte(params.to().toString());
                }
                return date;
            }))));
        }

        if (params.q() == null) {
            return Query.of(query -> query.bool(bool -> bool.filter(filters)));
        }
        return Query.of(query -> query.bool(bool -> bool
                .should(should -> should.multiMatch(match -> match
                        .query(params.q())
                        .type(TextQueryType.BestFields)
                        .fields("recipient_name^3", "description^1", "naics_description^0.5")))
                .should(should -> should.term(term -> term.field("piid").value(params.q()).boost(10f)))
                .minimumShouldMatch("1")
                .filter(filters)));
    }

    private static void terms(List<Query> filters, String field, List<String> values) {
        if (!values.isEmpty()) {
            var fieldValues = values.stream().map(FieldValue::of).toList();
            filters.add(Query.of(query -> query.terms(terms -> terms
                    .field(field)
                    .terms(value -> value.value(fieldValues)))));
        }
    }

    /** Award ID breaks every tie, so paging through equal scores or dates never repeats or skips an award. */
    private static List<SortOptions> sort(Sort sort) {
        var sorts = new ArrayList<SortOptions>();
        switch (sort) {
            case relevance -> {
                sorts.add(SortOptions.of(option -> option.score(score -> score.order(SortOrder.Desc))));
                sorts.add(field("last_action_date", SortOrder.Desc));
            }
            case newest -> sorts.add(field("last_action_date", SortOrder.Desc));
            case largest -> sorts.add(field("total_obligated", SortOrder.Desc));
            case recipient -> sorts.add(field("recipient_name.raw", SortOrder.Asc));
        }
        sorts.add(field("award_id", SortOrder.Asc));
        return sorts;
    }

    private static SortOptions field(String name, SortOrder order) {
        return SortOptions.of(option -> option.field(field -> field.field(name).order(order)));
    }

    private static SearchResults.Result result(Hit<Map<String, Object>> hit) {
        Map<String, Object> source = hit.source();
        var highlight = hit.highlight().get("description");
        return new SearchResults.Result(
                (String) source.get("award_id"),
                (String) source.get("piid"),
                (String) source.get("description"),
                highlight == null || highlight.isEmpty() ? null : highlight.getFirst(),
                new RecipientRef((String) source.get("recipient_uei"), (String) source.get("recipient_name")),
                new AgencyRef((String) source.get("awarding_toptier_code"),
                        (String) source.get("awarding_toptier_name"), (String) source.get("awarding_subtier_name")),
                money(source.get("total_obligated")),
                source.get("last_action_date") == null ? null : LocalDate.parse((String) source.get("last_action_date")),
                source.get("fiscal_year") == null ? null : ((Number) source.get("fiscal_year")).intValue(),
                (String) source.get("naics_code"),
                (String) source.get("pop_state_code"));
    }

    /** The source holds the exact decimal the indexer sent; its shortest double form restores it. */
    private static BigDecimal money(Object value) {
        return value == null ? null : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
