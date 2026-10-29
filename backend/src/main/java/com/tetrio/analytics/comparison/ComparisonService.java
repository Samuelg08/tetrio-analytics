package com.tetrio.analytics.comparison;

import com.tetrio.analytics.comparison.dto.ComparisonRequest;
import com.tetrio.analytics.comparison.dto.ComparisonResponse;
import com.tetrio.analytics.comparison.dto.MetricDifferencesDto;
import com.tetrio.analytics.player.PlayerMapper;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import com.tetrio.analytics.problem.FieldValidationError;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {

    private static final Pattern PLAYER_ID_PATTERN = Pattern.compile("^[0-9a-f]{24}$");

    private final PlayerPersistenceService playerPersistenceService;
    private final PlayerMapper playerMapper;
    private final Clock clock;

    public ComparisonService(
            PlayerPersistenceService playerPersistenceService,
            PlayerMapper playerMapper,
            Clock clock
    ) {
        this.playerPersistenceService = playerPersistenceService;
        this.playerMapper = playerMapper;
        this.clock = clock;
    }

    public ComparisonResponse comparePlayers(ComparisonRequest request) {
        List<FieldValidationError> errors = new ArrayList<>();
        String normalizedA = normalizeAndCollectError("playerAId", request != null ? request.playerAId() : null, errors);
        String normalizedB = normalizeAndCollectError("playerBId", request != null ? request.playerBId() : null, errors);

        if (normalizedA != null && normalizedB != null && normalizedA.equals(normalizedB)) {
            errors.add(new FieldValidationError(
                    "playerBId",
                    "Los identificadores playerAId y playerBId deben ser distintos"
            ));
        }

        if (!errors.isEmpty()) {
            throw new ApiProblemException(
                    ErrorCode.VALIDATION_FAILED,
                    "Uno o más parámetros no son válidos.",
                    errors
            );
        }

        PlayerDocument docA = playerPersistenceService.findByExternalPlayerId(normalizedA).orElse(null);
        PlayerDocument docB = playerPersistenceService.findByExternalPlayerId(normalizedB).orElse(null);

        if (docA == null && docB == null) {
            throw new ApiProblemException(
                    ErrorCode.PLAYER_NOT_FOUND,
                    "Los jugadores A (" + normalizedA + ") y B (" + normalizedB + ") no están almacenados en la base de datos local."
            );
        }
        if (docA == null) {
            throw new ApiProblemException(
                    ErrorCode.PLAYER_NOT_FOUND,
                    "El jugador A (" + normalizedA + ") no está almacenado en la base de datos local."
            );
        }
        if (docB == null) {
            throw new ApiProblemException(
                    ErrorCode.PLAYER_NOT_FOUND,
                    "El jugador B (" + normalizedB + ") no está almacenado en la base de datos local."
            );
        }

        PlayerProfileResponse profileA = playerMapper.fromPlayerDocument(docA, DataSource.CACHE);
        PlayerProfileResponse profileB = playerMapper.fromPlayerDocument(docB, DataSource.CACHE);

        LeagueSummaryDto leagueA = profileA.league();
        LeagueSummaryDto leagueB = profileB.league();

        MetricDifferencesDto differences = new MetricDifferencesDto(
                subtractOrNull(leagueA != null ? leagueA.tr() : null, leagueB != null ? leagueB.tr() : null),
                subtractOrNull(leagueA != null ? leagueA.glicko() : null, leagueB != null ? leagueB.glicko() : null),
                subtractOrNull(leagueA != null ? leagueA.gxe() : null, leagueB != null ? leagueB.gxe() : null),
                subtractOrNull(leagueA != null ? leagueA.apm() : null, leagueB != null ? leagueB.apm() : null),
                subtractOrNull(leagueA != null ? leagueA.pps() : null, leagueB != null ? leagueB.pps() : null),
                subtractOrNull(leagueA != null ? leagueA.vs() : null, leagueB != null ? leagueB.vs() : null)
        );

        return new ComparisonResponse(
                profileA,
                profileB,
                differences,
                null,
                Instant.now(clock)
        );
    }

    private static String normalizeAndCollectError(
            String fieldName,
            String rawId,
            List<FieldValidationError> errors
    ) {
        if (rawId == null) {
            errors.add(new FieldValidationError(fieldName, "Formato de " + fieldName + " no válido"));
            return null;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        if (!PLAYER_ID_PATTERN.matcher(normalized).matches()) {
            errors.add(new FieldValidationError(fieldName, "Formato de " + fieldName + " no válido"));
            return null;
        }
        return normalized;
    }

    private static Double subtractOrNull(Double valueA, Double valueB) {
        if (valueA == null || valueB == null) {
            return null;
        }
        return BigDecimal.valueOf(valueA).subtract(BigDecimal.valueOf(valueB)).doubleValue();
    }
}
