package com.tetrio.analytics.integration.tetrachannel.dto;

public record TetraChannelResponse<T>(
        T data,
        TetraChannelCache cache,
        String rawJson
) {

    public TetraChannelResponse(T data, TetraChannelCache cache) {
        this(data, cache, null);
    }
}
