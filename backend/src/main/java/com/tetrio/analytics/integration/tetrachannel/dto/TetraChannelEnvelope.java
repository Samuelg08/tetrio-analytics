package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraChannelEnvelope<T>(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") T data,
        @JsonProperty("cache") TetraChannelCache cache,
        @JsonProperty("error") TetraChannelError error
) {}
