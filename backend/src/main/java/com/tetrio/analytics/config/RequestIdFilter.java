package com.tetrio.analytics.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ID_ATTR = "com.tetrio.analytics.traceId";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String traceId = (incoming != null && VALID_REQUEST_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ID_ATTR, traceId);
        response.setHeader(HEADER_NAME, traceId);
        MDC.put("traceId", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    public static String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attr = request.getAttribute(REQUEST_ID_ATTR);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return UUID.randomUUID().toString();
    }
}
