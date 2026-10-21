package com.tetrio.analytics.player;

import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Consulta de perfiles y métricas competitivas de jugadores de TETR.IO")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping(value = "/by-username/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene el perfil y resumen competitivo de TETRA LEAGUE por username",
            description = "Normaliza y valida el username, consulta caché local en MongoDB y, si expiró o no existe, sincroniza desde TETRA CHANNEL respetando el rate limiter compartido."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del jugador obtenido correctamente"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Username con formato no válido (VALIDATION_FAILED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Jugador inexistente en TETRA CHANNEL (PLAYER_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Presupuesto local de solicitudes agotado (RATE_LIMITED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Respuesta inválida o error interno de TETRA CHANNEL (UPSTREAM_BAD_RESPONSE)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Timeout o límite 429 impuesto por TETRA CHANNEL (UPSTREAM_UNAVAILABLE)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public PlayerProfileResponse getByUsername(@PathVariable("username") String username) {
        return playerService.getByUsername(username);
    }

    @GetMapping(value = "/{playerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Obtiene el perfil y resumen competitivo de TETRA LEAGUE por playerId (MongoID de 24 hex)",
            description = "Valida el playerId, consulta caché local en MongoDB y, si expiró o no existe, sincroniza desde TETRA CHANNEL respetando el rate limiter compartido."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil del jugador obtenido correctamente"),
            @ApiResponse(
                    responseCode = "400",
                    description = "playerId con formato no válido (VALIDATION_FAILED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Jugador inexistente en TETRA CHANNEL (PLAYER_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Presupuesto local de solicitudes agotado (RATE_LIMITED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Respuesta inválida o error interno de TETRA CHANNEL (UPSTREAM_BAD_RESPONSE)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Timeout o límite 429 impuesto por TETRA CHANNEL (UPSTREAM_UNAVAILABLE)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public PlayerProfileResponse getByPlayerId(@PathVariable("playerId") String playerId) {
        return playerService.getByPlayerId(playerId);
    }
}
