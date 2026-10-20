package com.tetrio.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tetrioAnalyticsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TETR.IO Competitive Analytics API")
                        .version("0.2.0")
                        .description("API REST pública del backend Spring Boot conforme a docs/API_CONTRACTS.md (v0.2)."));
    }
}
