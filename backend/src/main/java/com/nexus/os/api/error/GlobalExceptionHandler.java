package com.nexus.os.api.error;

import com.nexus.os.billing.CostMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Translates domain exceptions to RFC 7807 Problem Details. Hides internal
 * detail (no stack traces over the wire) while preserving the stable error
 * codes that the frontend keys off.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CostMeter.BudgetExceededException.class)
    public ProblemDetail onBudgetExceeded(CostMeter.BudgetExceededException ex) {
        final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        pd.setTitle("Budget exceeded");
        pd.setType(java.net.URI.create("nexus:errors/budget-exceeded"));
        pd.setProperty("tenantId", ex.getTenantId());
        pd.setProperty("spent", ex.getSpent());
        pd.setProperty("budget", ex.getBudget());
        return pd;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail onNotFound(NoSuchElementException ex) {
        final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(java.net.URI.create("nexus:errors/not-found"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException ex) {
        final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(java.net.URI.create("nexus:errors/bad-request"));
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException ex) {
        // No tenant bound, etc.
        final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(java.net.URI.create("nexus:errors/missing-context"));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnhandled(Exception ex) {
        log.error("Unhandled exception", ex);
        final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        pd.setType(java.net.URI.create("nexus:errors/internal"));
        return pd;
    }
}
