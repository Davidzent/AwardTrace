package com.zntsns.awardtrace.pipeline.internal;

/** An event the pipeline can never write. Its reason code is stable, so the status page can count by cause. */
class InvalidEventException extends RuntimeException {

    private final String reason;

    InvalidEventException(String reason) {
        this(reason, null);
    }

    InvalidEventException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    String reason() {
        return reason;
    }
}
