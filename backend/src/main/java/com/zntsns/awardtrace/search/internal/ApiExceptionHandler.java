package com.zntsns.awardtrace.search.internal;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is RFC 9457 problem details (doc 07). The base class renders the framework's own errors and every
 * {@link org.springframework.web.ErrorResponseException} the controllers throw, and Spring Boot's equivalent handler
 * steps aside for it. This adds the two cases the framework can't know about: search being unavailable, and
 * anything unexpected.
 */
@RestControllerAdvice
@Profile("api")
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "30";

    /**
     * Only search reads Elasticsearch, and only its client throws I/O errors out of a controller. While Elasticsearch
     * is unreachable, or before its index exists, search is unavailable rather than broken.
     */
    @ExceptionHandler({IOException.class, ElasticsearchException.class})
    ResponseEntity<ProblemDetail> searchUnavailable(Exception exception) {
        if (exception instanceof ElasticsearchException elasticsearch && elasticsearch.status() < 500
                && elasticsearch.status() != 404) {
            return unexpected(exception);
        }
        log.warn("Search is unavailable: {}", exception.toString());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable; award and recipient pages still work");
        problem.setType(URI.create("search-unavailable"));
        problem.setTitle("Search unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    /** The details go to the log, not to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Request failed", exception);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The server failed to answer this request");
        problem.setType(URI.create("internal-error"));
        problem.setTitle("Internal error");
        return ResponseEntity.internalServerError().body(problem);
    }
}
