package com.tetrio.analytics.integration.tetrachannel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TetraPrisecter(
        BigDecimal pri,
        BigDecimal sec,
        BigDecimal ter
) {

    public boolean isValid() {
        return pri != null && sec != null && ter != null;
    }

    public String toCursorString() {
        if (!isValid()) {
            throw new IllegalStateException("Prisecter incompleto; no se puede construir cursor 'after'.");
        }
        return formatComponent(pri) + ":" + formatComponent(sec) + ":" + formatComponent(ter);
    }

    private static String formatComponent(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
