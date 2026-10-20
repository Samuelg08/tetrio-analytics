package com.tetrio.analytics.problem;

import java.util.List;

public class ApiProblemException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldValidationError> errors;
    private final Long retryAfterSeconds;

    public ApiProblemException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of(), null, null);
    }

    public ApiProblemException(ErrorCode errorCode, String detail, Throwable cause) {
        this(errorCode, detail, List.of(), null, cause);
    }

    public ApiProblemException(ErrorCode errorCode, String detail, List<FieldValidationError> errors) {
        this(errorCode, detail, errors, null, null);
    }

    public ApiProblemException(ErrorCode errorCode, String detail, Long retryAfterSeconds) {
        this(errorCode, detail, List.of(), retryAfterSeconds, null);
    }

    public ApiProblemException(
            ErrorCode errorCode,
            String detail,
            List<FieldValidationError> errors,
            Long retryAfterSeconds,
            Throwable cause
    ) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiProblemException validationFailed(String field, String message) {
        return new ApiProblemException(
                ErrorCode.VALIDATION_FAILED,
                "Uno o más parámetros no son válidos.",
                List.of(new FieldValidationError(field, message))
        );
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldValidationError> errors() {
        return errors;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
