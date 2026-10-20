package com.tetrio.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TetrioAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TetrioAnalyticsApplication.class, args);
    }
}
