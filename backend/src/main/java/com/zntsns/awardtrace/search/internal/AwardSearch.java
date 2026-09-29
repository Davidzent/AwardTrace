package com.zntsns.awardtrace.search.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Builds and runs the award search described in doc 06. */
@Component
@Profile("api")
class AwardSearch {

    /**
     * A multi-select facet: the parameter that selects its values, the field it filters and counts, how many values
     * it returns, and the field holding each value's label, if it has one.
     */
    private record Facet(String name, String field, int size, String labelField,
            Function<SearchParams, List<String>> selected) {
    }

    private static final List<Facet> FACETS = List.of(
            new Facet("agency", "awarding_toptier_code", 20, "awarding_toptier_name", SearchParams::agency),
            new Facet("category", "category", 20, null, SearchParams::category),
            new Facet("state", "pop_state_code", 60, null, SearchParams::state),
            new Facet("naics", "naics_code", 20, "naics_description", SearchParams::naics),
            new Facet("fiscal_year", "fiscal_year", 20, null,
                    params -> params.fiscalYear().stream().map(String::valueOf).toList()));

    private final ElasticsearchClient elasticsearch;

    AwardSearch(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    /**
     * Facet selections go in {@code post_filter}, so each facet can count its values without its own selection while
     * still applying every other one: selecting two agencies leaves the other agencies' counts visible.
     *
     * @param recipientUei limits the search to one recipient's awards, or null for everyone's
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    SearchResults search(SearchParams params, String recipientUei) throws IOException {
        Map<String, Query> selections = selections(params);
        var response = elasticsearch.search(request -> request
                .index(SearchIndexes.AWARDS)
                .query(query(params, recipientUei))
                .postFilter(all(selections.values()))
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
                .aggregations(aggregations(selections)),
                Map.class);

        var total = response.hits().total();
        // A sum over scaled_float values comes back as a double, so it is rounded to cents here.
        double sum = response.aggregations().get("matched").filter().aggregations().get("total_obligated").sum()
                .value();
        var totalObligated = BigDecimal.valueOf(sum).setScale(2, RoundingMode.HALF_UP);
        List<SearchResults.Result> results = response.hits().hits().stream()
                .map(hit -> result((Hit<Map<String, Object>>) (Hit) hit))
                .toList();
        return new SearchResults(total.value(), total.relation() == TotalHitsRelation.Gte, totalObligated,
                response.took(), params.page(), params.size(), results, facets(response.aggregations()));
    }

    record RecipientSuggestion(String uei, String name, long awardCount) {
    }

    /**
     * Recipients whose names contain every word typed, the last one as a prefix, most awards first. Each recipient's
     * newest award supplies its name.
     */
    List<RecipientSuggestion> suggestRecipients(String q, int size) throws IOException {
        var response = elasticsearch.search(request -> request
                .index(SearchIndexes.AWARDS)
                .size(0)
                .query(query -> query.multiMatch(match -> match
                        .query(q)
                        .type(TextQueryType.BoolPrefix)
                        .operator(Operator.And)
                        .fields("recipient_name.suggest", "recipient_name.suggest._2gram",
                                "recipient_name.suggest._3gram")))
                .aggregations("recipients", recipients -> recipients
                        .terms(terms -> terms.field("recipient_uei").size(size))
                        .aggregations("newest", newest -> newest.topHits(top -> top
                                .size(1)
                                .sort(field("last_action_date", SortOrder.Desc))
                                .source(source -> source.filter(filter -> filter.includes("recipient_name")))))),
                Map.class);
        return response.aggregations().get("recipients").sterms().buckets().array().stream()
                .map(bucket -> new RecipientSuggestion(bucket.key().stringValue(),
                        (String) bucket.aggregations().get("newest").topHits().hits().hits().getFirst().source()
                                .to(Map.class).get("recipient_name"),
                        bucket.docCount()))
                .toList();
    }

    /** One filter per facet with a selection, keyed by facet name. */
    private static Map<String, Query> selections(SearchParams params) {
        var selections = new LinkedHashMap<String, Query>();
        for (Facet facet : FACETS) {
            var values = facet.selected().apply(params);
            if (!values.isEmpty()) {
                var fieldValues = values.stream().map(FieldValue::of).toList();
                selections.put(facet.name(), Query.of(query -> query.terms(terms -> terms
                        .field(facet.field())
                        .terms(value -> value.value(fieldValues)))));
            }
        }
        return selections;
    }

    /** Each facet counts under every selection but its own; the dollar total sums under all of them. */
    private static Map<String, Aggregation> aggregations(Map<String, Query> selections) {
        var aggregations = new LinkedHashMap<String, Aggregation>();
        for (Facet facet : FACETS) {
            var others = selections.entrySet().stream()
                    .filter(selection -> !selection.getKey().equals(facet.name()))
                    .map(Map.Entry::getValue)
                    .toList();
            aggregations.put(facet.name(), Aggregation.of(aggregation -> aggregation
                    .filter(all(others))
                    .aggregations("values", values -> {
                        var terms = values.terms(field -> field.field(facet.field()).size(facet.size()));
                        return facet.labelField() == null ? terms : terms.aggregations("label", label -> label
                                .topHits(top -> top.size(1).source(source -> source
                                        .filter(filter -> filter.includes(facet.labelField())))));
                    })));
        }
        aggregations.put("matched", Aggregation.of(aggregation -> aggregation
                .filter(all(selections.values()))
                .aggregations("total_obligated", total -> total.sum(sum -> sum.field("total_obligated")))));
        return aggregations;
    }

    private static Map<String, List<SearchResults.FacetValue>> facets(Map<String, Aggregate> aggregations) {
        var facets = new LinkedHashMap<String, List<SearchResults.FacetValue>>();
        for (Facet facet : FACETS) {
            Aggregate values = aggregations.get(facet.name()).filter().aggregations().get("values");
            // Numeric fields, such as fiscal_year, come back as long terms; keywords as string terms.
            facets.put(facet.name(), values.isLterms()
                    ? values.lterms().buckets().array().stream()
                            .map(bucket -> new SearchResults.FacetValue(String.valueOf(bucket.key()),
                                    label(bucket.aggregations(), facet), bucket.docCount()))
                            .toList()
                    : values.sterms().buckets().array().stream()
                            .map(bucket -> new SearchResults.FacetValue(bucket.key().stringValue(),
                                    label(bucket.aggregations(), facet), bucket.docCount()))
                            .toList());
        }
        return facets;
    }

    private static String label(Map<String, Aggregate> bucket, Facet facet) {
        if (facet.labelField() == null) {
            return null;
        }
        var hits = bucket.get("label").topHits().hits().hits();
        return hits.isEmpty() ? null : (String) hits.getFirst().source().to(Map.class).get(facet.labelField());
    }

    private static Query all(Collection<Query> filters) {
        return Query.of(query -> query.bool(bool -> bool.filter(List.copyOf(filters))));
    }

    /**
     * A keyword matches recipient names first, then descriptions, then NAICS descriptions; a pasted contract number
     * outranks them all. The filters here are the ones that aren't facets; they narrow results and facet counts
     * alike, without scoring.
     */
    private static Query query(SearchParams params, String recipientUei) {
        var filters = new ArrayList<Query>();
        if (recipientUei != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("recipient_uei").value(recipientUei))));
        }
        if (params.categorySource() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("category_source")
                    .value(params.categorySource()))));
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
