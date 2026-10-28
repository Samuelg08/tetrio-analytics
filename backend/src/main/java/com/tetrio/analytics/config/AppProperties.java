package com.tetrio.analytics.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
        @NotNull @Valid CacheProperties cache,
        @NotNull @Valid AiServiceProperties aiService,
        @NotNull @Valid ExtractionProperties extraction
) {

    public static AppProperties of(
            CorsProperties cors,
            TetraChannelProperties tetraChannel,
            CacheProperties cache
    ) {
        return new AppProperties(
                cors,
                tetraChannel,
                cache,
                new AiServiceProperties("http://127.0.0.1:8000", 3000L),
                new ExtractionProperties(
                        false,
                        List.of("leaderboard_league"),
                        100,
                        0,
                        3,
                        5,
                        "../data/raw",
                        "../data/processed",
                        "",
                        ""
                )
        );
    }

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

    public record AiServiceProperties(
            @NotBlank String baseUrl,
            @Min(100) long timeoutMs
    ) {}

    public record ExtractionProperties(
            boolean enabled,
            @NotEmpty List<@NotBlank String> phases,
            @Min(1) @Max(100) int batchSize,
            @Min(0) int maxPages,
            @Min(0) int maxRetries,
            @Min(1) int maxConsecutiveErrors,
            @NotBlank String rawDir,
            @NotBlank String processedDir,
            String resumeRunId,
            String datasetVersion
    ) {}
}
