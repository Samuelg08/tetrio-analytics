package com.tetrio.analytics.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull @Valid CorsProperties cors,
        @NotNull @Valid TetraChannelProperties tetraChannel,
        @NotNull @Valid CacheProperties cache
) {

    public record CorsProperties(
            @NotEmpty List<@NotBlank String> allowedOrigins
    ) {}

    public record TetraChannelProperties(
            @NotBlank String baseUrl,
            @Min(100) long timeoutMs,
            @Min(0) long minIntervalMs,
            @Min(0) long acquireTimeoutMs,
            @NotBlank String userAgent
    ) {}

    public record CacheProperties(
            @Min(1) long ttlSeconds
    ) {}
}
