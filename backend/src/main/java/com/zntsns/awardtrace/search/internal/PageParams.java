package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.search.internal.SearchParams.FieldError;
import java.util.ArrayList;
import java.util.List;

/** {@code page} and {@code size} for the paginated lists other than search, under the same rules (doc 07). */
record PageParams(Integer page, Integer size) {

    /** Elasticsearch's default max_result_window; deeper pages are refused rather than served slowly. */
    static final int MAX_RESULT_WINDOW = 10_000;

    PageParams {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    List<FieldError> problems() {
        return problems(page, size);
    }

    static List<FieldError> problems(int page, int size) {
        var problems = new ArrayList<FieldError>();
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
