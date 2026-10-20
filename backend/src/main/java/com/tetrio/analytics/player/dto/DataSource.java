package com.tetrio.analytics.player.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DataSource {
    LIVE("live"),
    CACHE("cache"),
    STALE("stale");

    private final String value;

    DataSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
