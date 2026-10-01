package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.AwardQueries;
import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("api")
@RequestMapping("/api/v1/awards")
class AwardController {

    static final int MAX_TRANSACTIONS = 500;
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final AwardQueries awards;

    AwardController(AwardQueries awards) {
        this.awards = awards;
    }

    /**
     * The ETag is the award's index version, which changes whenever the award row does. Spring answers a request
     * whose If-None-Match matches it with 304 and no body.
     */
    @GetMapping("/{awardId}")
    ResponseEntity<AwardDetail> award(@PathVariable String awardId) {
        var found = awards.find(awardId, MAX_TRANSACTIONS).orElseThrow(() -> awardNotFound(awardId));
        return ResponseEntity.ok()
                .eTag(Long.toString(found.award().indexVersion()))
                .cacheControl(CACHE)
                .body(AwardDetail.of(found));
    }

    /** The subawards reported under a live award, newest first. */
    @GetMapping("/{awardId}/subawards")
    ResponseEntity<SubawardPage> subawards(@PathVariable String awardId,
            @ParameterObject @ModelAttribute PageParams params, BindingResult binding) {
        SearchController.rejectInvalid(params.problems(), binding);
        var found = awards.subawards(awardId, params.page(), params.size())
                .orElseThrow(() -> awardNotFound(awardId));
        return ResponseEntity.ok().cacheControl(CACHE).body(SubawardPage.of(found));
    }

    private static ErrorResponseException awardNotFound(String awardId) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No award with ID " + awardId);
        problem.setType(URI.create("award-not-found"));
        problem.setTitle("Award not found");
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }
}
