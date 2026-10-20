package com.tetrio.analytics.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(AppProperties properties) {
        List<String> origins = properties.cors().allowedOrigins().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS debe contener una lista explícita de orígenes sin el comodín '*'."
            );
        }
        this.allowedOrigins = origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Request-Id")
                .exposedHeaders("X-Request-Id", "Retry-After")
                .allowCredentials(false);
    }
}
