package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraLeagueSummaryData(
        @JsonProperty("gamesplayed") Integer gamesPlayed,
        @JsonProperty("gameswon") Integer gamesWon,
        @JsonProperty("tr") Double tr,
        @JsonProperty("glicko") Double glicko,
        @JsonProperty("rd") Double rd,
        @JsonProperty("gxe") Double gxe,
        @JsonProperty("rank") String rank,
        @JsonProperty("bestrank") String bestRank,
        @JsonProperty("apm") Double apm,
        @JsonProperty("pps") Double pps,
        @JsonProperty("vs") Double vs,
        @JsonProperty("standing") Integer standing,
        @JsonProperty("percentile") Double percentile,
        @JsonProperty("decaying") Boolean decaying
) {}
