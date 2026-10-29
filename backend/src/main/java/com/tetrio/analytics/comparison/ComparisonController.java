package com.tetrio.analytics.comparison;

import com.tetrio.analytics.comparison.dto.ComparisonRequest;
import com.tetrio.analytics.comparison.dto.ComparisonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comparisons")
@Tag(name = "Comparisons", description = "Comparación descriptiva de jugadores almacenados en MongoDB")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Compara descriptivamente las métricas competitivas de dos jugadores almacenados",
            description = "Calcula las diferencias (A - B) entre dos jugadores distintos identificados por su playerId (MongoID de 24 hex). Mantiene headToHead en null hasta que exista un modelo H2H evaluado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparación descriptiva generada correctamente"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Identificadores inválidos, iguales o cuerpo malformado (VALIDATION_FAILED / MALFORMED_REQUEST)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Uno o ambos jugadores no están almacenados en la base de datos local (PLAYER_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ComparisonResponse comparePlayers(@RequestBody ComparisonRequest request) {
        return comparisonService.comparePlayers(request);
    }
}
