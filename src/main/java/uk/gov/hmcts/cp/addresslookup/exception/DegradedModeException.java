package uk.gov.hmcts.cp.addresslookup.exception;

import uk.gov.hmcts.cp.openapi.model.al.DegradedReason;

/**
 * Signals that OS Places could not serve a request (timeout, rate-limit, auth failure, or an
 * unexpected/malformed response). Mapped by the controller advice to the contract's
 * {@code DegradedResponse} 503 shape - never treated as a 500, since a degraded upstream is an
 * expected, contract-defined outcome rather than a bug.
 */
public class DegradedModeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DegradedReason reason;
    private final Integer retryAfterSeconds;

    public DegradedModeException(final DegradedReason reason, final Integer retryAfterSeconds, final String message) {
        super(message);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public DegradedModeException(final DegradedReason reason, final Integer retryAfterSeconds, final String message,
            final Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public DegradedReason getReason() {
        return reason;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
