package uk.gov.hmcts.cp.addresslookup.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.addresslookup.exception.DegradedModeException;
import uk.gov.hmcts.cp.openapi.model.al.DegradedResponse;
import uk.gov.hmcts.cp.openapi.model.al.ErrorResponse;

/**
 * Centralised exception mapping for the Address Lookup REST controllers - keeps controllers
 * clean and ensures consistent, traceable {@link ErrorResponse}/{@link DegradedResponse} bodies.
 *
 * <p>Every operation in the contract declares its own vendor-specific media type (e.g.
 * {@code application/vnd.addresslookup-service.addresses-postcode+json}) on its 400/503
 * responses, not just its 200. Since one advice is shared across (future) operations with
 * different vendor types, responses here echo back the caller's requested vendor media type from
 * the {@code Accept} header rather than hardcoding one, falling back to plain JSON when the
 * caller didn't ask for a specific vendor type.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String VENDOR_MEDIA_TYPE_PREFIX = "application/vnd.addresslookup-service.";

    private final Tracer tracer;

    @ExceptionHandler(DegradedModeException.class)
    public ResponseEntity<DegradedResponse> onDegraded(final DegradedModeException ex, final HttpServletRequest request) {
        log.warn("OS Places degraded: reason={}", ex.getReason(), ex);
        final DegradedResponse body = new DegradedResponse(true, ex.getReason());
        if (ex.getRetryAfterSeconds() != null) {
            body.setRetryAfterSeconds(ex.getRetryAfterSeconds());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(responseMediaType(request))
                .body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> onResponseStatus(final ResponseStatusException ex, final HttpServletRequest request) {
        log.warn("ResponseStatusException status={} reason={}", ex.getStatusCode(), ex.getReason(), ex);
        final String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(responseMediaType(request))
                .body(errorBody(String.valueOf(ex.getStatusCode().value()), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> onMissingParam(final MissingServletRequestParameterException ex, final HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(responseMediaType(request))
                .body(errorBody(String.valueOf(HttpStatus.BAD_REQUEST.value()), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> onTypeMismatch(final MethodArgumentTypeMismatchException ex, final HttpServletRequest request) {
        final String details = "'" + ex.getName() + "' has an invalid value";
        log.warn("Argument type mismatch: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(responseMediaType(request))
                .body(errorBody(String.valueOf(HttpStatus.BAD_REQUEST.value()), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> onConstraint(final ConstraintViolationException ex, final HttpServletRequest request) {
        final String details = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::formatViolation)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Constraint violation: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(responseMediaType(request))
                .body(errorBody(String.valueOf(HttpStatus.BAD_REQUEST.value()), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpected(final Exception ex, final HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(responseMediaType(request))
                .body(errorBody(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "Unexpected error"));
    }

    private ErrorResponse errorBody(final String code, final String message) {
        return new ErrorResponse()
                .error(code)
                .message(message)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .traceId(traceId());
    }

    // Deliberately broad: currentSpan()/context() can throw various runtime exceptions depending
    // on the tracer implementation and whether a span is active, and a trace-ID lookup failure
    // must never break the actual error response being built.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String traceId() {
        String id = null;
        try {
            id = Objects.requireNonNull(tracer.currentSpan()).context().traceId();
        } catch (final RuntimeException ignored) {
            // id stays null (already initialised above)
        }
        return id;
    }

    private static MediaType responseMediaType(final HttpServletRequest request) {
        MediaType mediaType = MediaType.APPLICATION_JSON;
        final String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null) {
            for (final String candidate : accept.split(",")) {
                final String trimmed = candidate.trim();
                if (trimmed.startsWith(VENDOR_MEDIA_TYPE_PREFIX)) {
                    try {
                        mediaType = MediaType.parseMediaType(trimmed);
                    } catch (final InvalidMediaTypeException ignored) {
                        mediaType = MediaType.APPLICATION_JSON;
                    }
                    break;
                }
            }
        }
        return mediaType;
    }

    private static String formatViolation(final ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + (violation.getMessage() != null ? violation.getMessage() : "is invalid");
    }

    // Note: HandlerMethodValidationException (thrown for @RequestParam bean-validation failures,
    // e.g. postcode > 10 chars, since @Validated is on AddressSearchApi) extends
    // ResponseStatusException, so onResponseStatus above already covers it - no separate handler
    // needed.
}
