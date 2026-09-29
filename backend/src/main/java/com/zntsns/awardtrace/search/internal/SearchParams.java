package com.zntsns.awardtrace.search.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;

/**
 * The query parameters of {@code GET /api/v1/awards/search} (doc 07). Repeatable parameters match any of their
 * values; different parameters must all match.
 */
record SearchParams(
        String q,
        List<String> agency,
        List<String> category,
        List<String> state,
        List<String> naics,
        @BindParam("fiscal_year") List<Integer> fiscalYear,
        @BindParam("min_amount") BigDecimal minAmount,
        @BindParam("max_amount") BigDecimal maxAmount,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @BindParam("category_source") String categorySource,
        String sort,
        Integer page,
        Integer size) {

    /** Elasticsearch's default max_result_window; deeper pages are refused rather than served slowly. */
    static final int MAX_RESULT_WINDOW = 10_000;

    enum Sort { relevance, newest, largest, recipient }

    record FieldError(String field, String message) {
    }

    SearchParams {
        q = q == null || q.isBlank() ? null : q.strip();
        agency = agency == null ? List.of() : List.copyOf(agency);
        category = category == null ? List.of() : List.copyOf(category);
        state = state == null ? List.of() : List.copyOf(state);
        naics = naics == null ? List.of() : List.copyOf(naics);
        fiscalYear = fiscalYear == null ? List.of() : List.copyOf(fiscalYear);
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    /** Relevance when there is a keyword to be relevant to, otherwise newest first. */
    Sort effectiveSort() {
        return sort == null ? (q == null ? Sort.newest : Sort.relevance) : Sort.valueOf(sort);
    }

    List<FieldError> problems() {
        var problems = new ArrayList<FieldError>();
        if (q != null && q.length() > 200) {
            problems.add(new FieldError("q", "must be at most 200 characters"));
        }
        if (fiscalYear.stream().anyMatch(year -> year < 2025)) {
            problems.add(new FieldError("fiscal_year", "must be 2025 or later"));
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            problems.add(new FieldError("max_amount", "must not be less than min_amount"));
        }
        if (from != null && to != null && from.isAfter(to)) {
            problems.add(new FieldError("to", "must not be before from"));
        }
        if (categorySource != null && !Set.of("llm", "baseline").contains(categorySource)) {
            problems.add(new FieldError("category_source", "must be llm or baseline"));
        }
        if (sort != null && Set.of(Sort.values()).stream().noneMatch(value -> value.name().equals(sort))) {
            problems.add(new FieldError("sort", "must be relevance, newest, largest, or recipient"));
        }
        if (page < 1) {
            problems.add(new FieldError("page", "must be 1 or more"));
        }
        if (size < 1 || size > 50) {
            problems.add(new FieldError("size", "must be between 1 and 50"));
        } else if (page >= 1 && (long) page * size > MAX_RESULT_WINDOW) {
            problems.add(new FieldError("page", "page times size may not exceed 10,000; refine the search instead"));
        }
        return problems;
    }
}
