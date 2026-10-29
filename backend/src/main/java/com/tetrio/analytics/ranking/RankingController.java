package com.tetrio.analytics.ranking;

import com.tetrio.analytics.ranking.dto.LocalRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@Tag(name = "Rankings", description = "Clasificaciones locales sobre el universo de jugadores almacenados en MongoDB")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping(value = "/local", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene el ranking local paginado según la métrica competitiva seleccionada",
            description = "Ordena de forma descendente los jugadores almacenados en la base de datos del proyecto según 'metric' (tr, apm, pps, vs), excluyendo valores nulos y exigiendo 'ranked = true' cuando metric=tr."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de ranking local y metadatos del universo obtenidos correctamente"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parámetros de métrica, país o paginación inválidos (VALIDATION_FAILED / MALFORMED_REQUEST)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public LocalRankingResponse getLocalRanking(
            @RequestParam(name = "metric", required = false) String metric,
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return rankingService.getLocalRanking(metric, country, page, size);
    }
}
