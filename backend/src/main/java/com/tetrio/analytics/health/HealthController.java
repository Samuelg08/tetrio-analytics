package com.tetrio.analytics.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Estado de disponibilidad del proceso backend")
public class HealthController {

    public record HealthResponse(String status) {}

    @GetMapping("/api/health")
    @Operation(summary = "Comprueba que el proceso del backend responde")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
