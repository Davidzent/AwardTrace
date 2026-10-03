package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.Category;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
class SearchController {

    static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();
    private static final CacheControl TAXONOMY_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final AwardSearch search;
    private final Taxonomy taxonomy;

    SearchController(AwardSearch search, Taxonomy taxonomy) {
        this.search = search;
        this.taxonomy = taxonomy;
    }

    @GetMapping("/api/v1/awards/search")
    ResponseEntity<SearchResults> search(@ParameterObject @ModelAttribute SearchParams params, BindingResult binding)
            throws IOException {
        rejectInvalid(params.problems(), binding);
        return ResponseEntity.ok().cacheControl(CACHE).body(search.search(params, null));
    }

    /** The taxonomy in display order, for facet labels and help text. */
    @GetMapping("/api/v1/categories")
    ResponseEntity<List<Category>> categories() {
        return ResponseEntity.ok().cacheControl(TAXONOMY_CACHE).body(taxonomy.categories());
    }

    /**
     * Binding errors, such as {@code page=abc}, and the parameters' rule violations are reported together, one per
     * field.
     */
    static void rejectInvalid(List<SearchParams.FieldError> rules, BindingResult binding) {
        var problems = new ArrayList<SearchParams.FieldError>();
        // Spring reports binding errors under the request parameter's name, such as fiscal_year.
        binding.getFieldErrors().forEach(error -> problems.add(new SearchParams.FieldError(error.getField(),
                "is not valid")));
        // A field that failed to bind is left at its default, so only its binding error is reported.
        rules.stream()
                .filter(problem -> problems.stream().noneMatch(reported -> reported.field().equals(problem.field())))
                .forEach(problems::add);
        if (!problems.isEmpty()) {
            throw invalidParameters(problems);
        }
    }

    static ErrorResponseException invalidParameters(List<SearchParams.FieldError> problems) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The search parameters are invalid");
        problem.setType(URI.create("invalid-search-parameters"));
        problem.setTitle("Invalid search parameters");
        problem.setProperty("errors", problems);
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
}
