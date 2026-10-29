package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraRecordSummaryData(
        @JsonProperty("record") TetraRecordEntry record,
        @JsonProperty("rank") Integer rank,
        @JsonProperty("rank_local") Integer rankLocal
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TetraRecordEntry(
            @JsonProperty("_id") String id,
            @JsonProperty("replayid") String replayId,
            @JsonProperty("gamemode") String gamemode,
            @JsonProperty("ts") String ts,
            @JsonProperty("results") TetraRecordResults results
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TetraRecordResults(
            @JsonProperty("aggregatestats") TetraRecordAggregateStats aggregateStats,
            @JsonProperty("stats") TetraRecordStats stats
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TetraRecordAggregateStats(
            @JsonProperty("apm") Double apm,
            @JsonProperty("pps") Double pps,
            @JsonProperty("vsscore") Double vsScore
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TetraRecordStats(
            @JsonProperty("finaltime") Double finalTime,
            @JsonProperty("score") Long score,
            @JsonProperty("lines") Integer lines,
            @JsonProperty("level") Integer level,
            @JsonProperty("inputs") Integer inputs,
            @JsonProperty("piecesplaced") Integer piecesPlaced,
            @JsonProperty("tspins") Integer tSpins,
            @JsonProperty("finesse") TetraRecordFinesse finesse
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TetraRecordFinesse(
            @JsonProperty("combo") Integer combo,
            @JsonProperty("faults") Integer faults,
            @JsonProperty("perfectpieces") Integer perfectPieces
    ) {}
}
