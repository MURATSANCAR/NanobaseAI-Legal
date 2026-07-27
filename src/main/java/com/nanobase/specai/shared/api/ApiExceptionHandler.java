package com.nanobase.specai.shared.api;

import com.nanobase.specai.shared.security.MissingTenantException;
import com.nanobase.specai.document.application.InvalidDocumentException;
import com.nanobase.specai.tender.application.TenderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(TenderNotFoundException.class)
    ProblemDetail notFound(TenderNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Tender not found", exception.getMessage(), request);
    }

    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail unauthorized(MissingTenantException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Tenant context unavailable", exception.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail conflict(ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
            "Resource changed since it was read; reload and retry", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
            "One or more request fields are invalid", request);
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(),
                (first, ignored) -> first));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail invalidDocument(InvalidDocumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document", exception.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setInstance(URI.create(request.getRequestURI()));
        return detail;
    }
}
