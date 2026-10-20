package com.tetrio.analytics.problem;

public record FieldValidationError(
        String field,
        String message
) {}
