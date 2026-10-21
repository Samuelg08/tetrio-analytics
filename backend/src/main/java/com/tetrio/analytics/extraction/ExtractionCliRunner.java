package com.tetrio.analytics.extraction;

import com.tetrio.analytics.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("extraction")
@ConditionalOnProperty(prefix = "app.extraction", name = "enabled", havingValue = "true")
public class ExtractionCliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCliRunner.class);

    private final LeaderboardExtractionService extractionService;
    private final AppProperties properties;

    public ExtractionCliRunner(
            LeaderboardExtractionService extractionService,
            AppProperties properties
    ) {
        this.extractionService = extractionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.extraction().phases().contains(LeaderboardExtractionService.PHASE_LEADERBOARD_LEAGUE)) {
            log.info("Iniciando extracción CLI de Fase 1 ({})", LeaderboardExtractionService.PHASE_LEADERBOARD_LEAGUE);
            ExtractionCheckpoint result = extractionService.runPhase1Leaderboard();
            log.info(
                    "Extracción finalizada con estado='{}', runId='{}', páginas={}, jugadoresÚnicos={}",
                    result.status(),
                    result.runId(),
                    result.pagesCompleted(),
                    result.counters().uniquePlayers()
            );
            if (!"completed".equals(result.status())) {
                throw new IllegalStateException(
                        "El pipeline se detuvo en estado '" + result.status() + "' para runId=" + result.runId()
                );
            }
        }
    }
}
