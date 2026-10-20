package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraChannelError(
        @JsonProperty("msg") String msg
) {}
