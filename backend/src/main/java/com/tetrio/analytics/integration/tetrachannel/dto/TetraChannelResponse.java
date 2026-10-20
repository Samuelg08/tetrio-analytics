package com.tetrio.analytics.integration.tetrachannel.dto;

public record TetraChannelResponse<T>(
        T data,
        TetraChannelCache cache
) {}
