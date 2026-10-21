package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraLeaderboardEntry(
        @JsonProperty("_id") String id,
        String username,
        String country,
        Double xp,
        Double gametime,
        Double ar,
        @JsonProperty("gamesplayed") Integer gamesPlayed,
        @JsonProperty("gameswon") Integer gamesWon,
        TetraLeagueSummaryData league,
        TetraPrisecter p
) {

    public TetraUserData toUserData() {
        return new TetraUserData(id, username, country, xp, gametime);
    }
}
