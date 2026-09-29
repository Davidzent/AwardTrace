package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.RecipientQueries;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
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
@RequestMapping("/api/v1/recipients")
class RecipientController {

    static final int TOP = 5;
    private static final Pattern UEI = Pattern.compile("[A-Za-z0-9]{12}");
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final RecipientQueries recipients;
    private final AwardSearch search;

    RecipientController(RecipientQueries recipients, AwardSearch search) {
        this.recipients = recipients;
        this.search = search;
    }

    @GetMapping("/{uei}")
    ResponseEntity<RecipientDetail> recipient(@PathVariable String uei) {
        var profile = recipients.find(normalized(uei), TOP)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "recipient-not-found", "Recipient not found",
                        "No recipient with UEI " + uei));
        return ResponseEntity.ok().cacheControl(CACHE).body(RecipientDetail.of(profile));
    }

    /**
     * Search limited to one recipient, with the same parameters and response. An unknown UEI finds nothing rather
     * than 404, as any search can.
     */
    @GetMapping("/{uei}/awards")
    ResponseEntity<SearchResults> awards(@PathVariable String uei, @ModelAttribute SearchParams params,
            BindingResult binding) throws IOException {
        String normalized = normalized(uei);
        SearchController.rejectInvalid(params, binding);
        return ResponseEntity.ok().cacheControl(SearchController.CACHE).body(search.search(params, normalized));
    }

    /** UEIs are stored in upper case, so a pasted lower-case one still finds its recipient. */
    private static String normalized(String uei) {
        if (!UEI.matcher(uei).matches()) {
            throw problem(HttpStatus.BAD_REQUEST, "invalid-uei", "Invalid UEI", "A UEI is 12 letters and digits");
        }
        return uei.toUpperCase(Locale.ROOT);
    }

    private static ErrorResponseException problem(HttpStatus status, String type, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        return new ErrorResponseException(status, problem, null);
    }
}
