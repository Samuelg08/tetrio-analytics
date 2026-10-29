package com.tetrio.analytics.comparison.dto;

public record MetricDifferencesDto(
        Double tr,
        Double glicko,
        Double gxe,
        Double apm,
        Double pps,
        Double vs
) {}
