package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraSeasonHistoryEntry(
        @JsonProperty("_id") String id,
        String season,
        String username,
        String country,
        Integer placement,
        @JsonProperty("gamesplayed") Integer gamesPlayed,
        @JsonProperty("gameswon") Integer gamesWon,
        Double glicko,
        Double gxe,
        Double tr,
        Double rd,
        String rank,
        @JsonProperty("bestrank") String bestRank,
        Boolean ranked,
        TetraPrisecter p,
        Double apm,
        Double pps,
        Double vs
) {

    public TetraUserData toUserData() {
        return new TetraUserData(id, username, country, null, null);
    }

    public TetraLeagueSummaryData toLeagueSummaryData() {
        return new TetraLeagueSummaryData(
                gamesPlayed,
                gamesWon,
                tr,
                glicko,
                rd,
                gxe,
                rank,
                bestRank,
                apm,
                pps,
                vs,
                placement,
                null,
                false
        );
    }

    public TetraLeaderboardEntry toLeaderboardEntry() {
        return new TetraLeaderboardEntry(
                id,
                username,
                country,
                null,
                null,
                null,
                gamesPlayed,
                gamesWon,
                toLeagueSummaryData(),
                p
        );
    }
}
