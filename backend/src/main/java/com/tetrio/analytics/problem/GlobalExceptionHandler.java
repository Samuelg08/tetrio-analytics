package com.tetrio.analytics.problem;

import com.tetrio.analytics.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetail> handleApiProblem(
            ApiProblemException ex,
            HttpServletRequest request
    ) {
        ErrorCode code = ex.errorCode();
        ProblemDetail problem = buildProblemDetail(code, ex.getMessage(), request);

        if (code == ErrorCode.VALIDATION_FAILED && !ex.errors().isEmpty()) {
            problem.setProperty("errors", ex.errors());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.retryAfterSeconds() != null && ex.retryAfterSeconds() > 0) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }

        return new ResponseEntity<>(problem, headers, code.httpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldValidationError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ProblemDetail problem = buildProblemDetail(
                ErrorCode.VALIDATION_FAILED,
                "Uno o más parámetros no son válidos.",
                request
        );
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "parameter";
                    int dot = path.lastIndexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return new FieldValidationError(field, cv.getMessage());
                })
                .toList();

        ProblemDetail problem = buildProblemDetail(
                ErrorCode.VALIDATION_FAILED,
                "Uno o más parámetros no son válidos.",
                request
        );
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                ErrorCode.MALFORMED_REQUEST,
                "La solicitud tiene un formato o tipo de datos no válido.",
                request
        );
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = buildProblemDetail(
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe.",
                request
        );
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = buildProblemDetail(
                ErrorCode.INTERNAL_ERROR,
                "Se produjo un error interno inesperado.",
                request
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ProblemDetail buildProblemDetail(
            ErrorCode code,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.httpStatus(), detail);
        problem.setType(code.typeUri());
        problem.setTitle(code.title());
        if (request != null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", RequestIdFilter.resolveTraceId(request));
        return problem;
    }
}
