package com.nanobase.specai.shared.api;

import com.nanobase.specai.document.application.InvalidDocumentException;
import com.nanobase.specai.identity.application.InvalidCredentialsException;
import com.nanobase.specai.operations.application.ResourceQuotaExceededException;
import com.nanobase.specai.operations.application.WorkloadCapacityException;
import com.nanobase.specai.shared.security.MissingTenantException;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessDeniedException;
import com.nanobase.specai.tender.application.TenderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(TenderNotFoundException.class)
    ProblemDetail notFound(TenderNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Tender project not found",
            "TENDER_PROJECT_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({ProjectAccessDeniedException.class, AccessDeniedException.class})
    ProblemDetail forbidden(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED",
            exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail unauthorized(MissingTenantException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Tenant context unavailable",
            "TENANT_CONTEXT_REQUIRED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException exception,
                                     HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
            "INVALID_CREDENTIALS", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail conflict(ObjectOptimisticLockingFailureException exception,
                           HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", "OPTIMISTIC_LOCK_FAILED",
            "Resource changed since it was read; reload and retry", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList();
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
            "One or more request fields are invalid", request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException exception,
                                   HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED",
            exception.getMessage(), request,
            List.of(new FieldError(exception.getParameterName(), "is required")));
    }

    @ExceptionHandler({InvalidDocumentException.class, IllegalArgumentException.class})
    ProblemDetail unprocessable(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Request could not be processed",
            exception instanceof InvalidDocumentException
                ? "DOCUMENT_UPLOAD_FAILED" : "BUSINESS_RULE_VIOLATION",
            exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail invalidState(IllegalStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Resource state conflict",
            "STATE_TRANSITION_REJECTED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large",
            "DOCUMENT_SIZE_LIMIT_EXCEEDED", "File exceeds the configured size limit",
            request, List.of());
    }

    @ExceptionHandler(ResourceQuotaExceededException.class)
    ProblemDetail quota(ResourceQuotaExceededException exception, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Resource quota exceeded",
            "RESOURCE_QUOTA_EXCEEDED", exception.getMessage(), request, List.of());
        detail.setProperty("quotaCode", exception.quotaCode());
        detail.setProperty("limit", exception.limit());
        detail.setProperty("requestedUsage", exception.currentUsage());
        return detail;
    }

    @ExceptionHandler(WorkloadCapacityException.class)
    ResponseEntity<ProblemDetail> capacity(WorkloadCapacityException exception,
                                           HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.SERVICE_UNAVAILABLE,
            "Workload capacity unavailable", "WORKLOAD_CAPACITY_UNAVAILABLE",
            exception.getMessage(), request, List.of());
        detail.setProperty("decision", exception.decision().name());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "30")
            .body(detail);
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String message,
                                  HttpServletRequest request, List<FieldError> fieldErrors) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("https://errors.nanobase.ai/" + code.toLowerCase()
            .replace('_', '-')));
        detail.setTitle(title);
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        detail.setProperty("correlationId",
            RequestContext.current().correlationId().toString());
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    record FieldError(String field, String message) {
    }
}
