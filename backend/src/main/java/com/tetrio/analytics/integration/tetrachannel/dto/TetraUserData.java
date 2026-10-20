package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraUserData(
        @JsonProperty("_id") String id,
        @JsonProperty("username") String username,
        @JsonProperty("country") String country,
        @JsonProperty("xp") Double xp,
        @JsonProperty("gametime") Double gametime
) {}
