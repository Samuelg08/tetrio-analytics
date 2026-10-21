package com.tetrio.analytics.common;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = totalItems <= 0L ? 0 : (int) ((totalItems + size - 1L) / size);
        return new PageResponse<>(
                items == null ? List.of() : List.copyOf(items),
                page,
                size,
                totalItems,
                totalPages
        );
    }
}
