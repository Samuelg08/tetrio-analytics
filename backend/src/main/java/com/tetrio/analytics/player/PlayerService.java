package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.problem.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,16}$");

    private final TetraChannelClient tetraChannelClient;
    private final PlayerMapper playerMapper;
    private final Clock clock;

    public PlayerService(
            TetraChannelClient tetraChannelClient,
            PlayerMapper playerMapper,
            Clock clock
    ) {
        this.tetraChannelClient = tetraChannelClient;
        this.playerMapper = playerMapper;
        this.clock = clock;
    }

    public PlayerProfileResponse getByUsername(String rawUsername) {
        String normalizedUsername = normalizeAndValidateUsername(rawUsername);
        String sessionId = "live-" + UUID.randomUUID();

        TetraChannelResponse<TetraUserData> userResponse =
                tetraChannelClient.fetchUser(normalizedUsername, sessionId);
        TetraChannelResponse<TetraLeagueSummaryData> leagueResponse =
                tetraChannelClient.fetchLeagueSummary(normalizedUsername, sessionId);

        Instant syncedAt = Instant.now(clock);
        return playerMapper.toPlayerProfile(
                userResponse.data(),
                leagueResponse.data(),
                syncedAt,
                DataSource.LIVE
        );
    }

    public static String normalizeAndValidateUsername(String rawUsername) {
        if (rawUsername == null) {
            throw ApiProblemException.validationFailed("username", "Formato de username no válido");
        }
        String normalized = rawUsername.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw ApiProblemException.validationFailed("username", "Formato de username no válido");
        }
        return normalized;
    }
}
