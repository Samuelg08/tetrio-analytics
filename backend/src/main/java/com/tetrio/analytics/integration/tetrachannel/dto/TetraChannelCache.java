package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraChannelCache(
        @JsonProperty("status") String status,
        @JsonProperty("cached_at") Long cachedAt,
        @JsonProperty("cached_until") Long cachedUntil
) {}
