package com.tetrio.analytics.problem;

import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "/problems/validation-failed", "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "/problems/malformed-request", "Malformed request"),
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "/problems/player-not-found", "Player not found"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "/problems/resource-not-found", "Resource not found"),
    PREDICTION_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "/problems/prediction-not-applicable", "Prediction not applicable"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "/problems/rate-limited", "Rate limit exceeded"),
    UPSTREAM_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "/problems/upstream-bad-response", "Upstream bad response"),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "/problems/upstream-unavailable", "Upstream service unavailable"),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "/problems/ai-service-unavailable", "AI service unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "/problems/internal-error", "Internal server error");

    private final HttpStatus httpStatus;
    private final URI typeUri;
    private final String title;

    ErrorCode(HttpStatus httpStatus, String typePath, String title) {
        this.httpStatus = httpStatus;
        this.typeUri = URI.create(typePath);
        this.title = title;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public URI typeUri() {
        return typeUri;
    }

    public String title() {
        return title;
    }
}
