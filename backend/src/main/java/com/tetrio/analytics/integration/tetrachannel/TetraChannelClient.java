package com.tetrio.analytics.integration.tetrachannel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelEnvelope;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraGeneralStatsData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraRecordSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraSeasonHistoryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TetraChannelClient {

    public static final String SESSION_HEADER = "X-Session-ID";
    private static final long DEFAULT_UPSTREAM_RETRY_AFTER_SECONDS = 10L;
    private static final java.util.regex.Pattern PRISECTER_CURSOR_PATTERN =
            java.util.regex.Pattern.compile("^[0-9.+-]+:[0-9.+-]+:[0-9.+-]+$");

    private final RestClient restClient;
    private final TetraChannelRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final String userAgent;

    public TetraChannelClient(
            @Qualifier("tetraChannelRestClientBuilder") RestClient.Builder tetraChannelRestClientBuilder,
            TetraChannelRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            AppProperties properties
    ) {
        this.restClient = tetraChannelRestClientBuilder.build();
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.userAgent = properties.tetraChannel().userAgent();
    }

    public TetraChannelResponse<TetraUserData> fetchUser(String username, String sessionId) {
        return get(
                "/users/{username}",
                new TypeReference<TetraChannelEnvelope<TetraUserData>>() {},
                sessionId,
                username
        );
    }

    public TetraChannelResponse<TetraLeagueSummaryData> fetchLeagueSummary(String username, String sessionId) {
        return get(
                "/users/{username}/summaries/league",
                new TypeReference<TetraChannelEnvelope<TetraLeagueSummaryData>>() {},
                sessionId,
                username
        );
    }

    public TetraChannelResponse<TetraRecordSummaryData> fetchRecordSummary(
            String userIdentifier,
            String mode,
            String sessionId
    ) {
        return get(
                "/users/{userIdentifier}/summaries/{mode}",
                new TypeReference<TetraChannelEnvelope<TetraRecordSummaryData>>() {},
                sessionId,
                userIdentifier,
                mode
        );
    }

    public TetraChannelResponse<TetraLeaderboardData> fetchLeagueLeaderboardPage(
            int limit,
            String afterCursor,
            String sessionId
    ) {
        if (afterCursor == null || afterCursor.isBlank()) {
            return get(
                    "/users/by/league?limit={limit}",
                    new TypeReference<TetraChannelEnvelope<TetraLeaderboardData>>() {},
                    sessionId,
                    limit
            );
        }
        String trimmedCursor = afterCursor.trim();
        if (!PRISECTER_CURSOR_PATTERN.matcher(trimmedCursor).matches()) {
            throw new IllegalArgumentException("Cursor prisecter con formato inválido: " + trimmedCursor);
        }
        return get(
                "/users/by/league?limit={limit}&after=" + trimmedCursor,
                new TypeReference<TetraChannelEnvelope<TetraLeaderboardData>>() {},
                sessionId,
                limit
        );
    }

    public TetraChannelResponse<TetraSeasonHistoryData> fetchSeasonLeaderboardPage(
            String season,
            int limit,
            String afterCursor,
            String sessionId
    ) {
        if (season == null || season.isBlank()) {
            throw new IllegalArgumentException("El parámetro season no puede ser vacío.");
        }
        String trimmedSeason = season.trim();
        if (afterCursor == null || afterCursor.isBlank()) {
            return get(
                    "/users/history/league/{season}?limit={limit}",
                    new TypeReference<TetraChannelEnvelope<TetraSeasonHistoryData>>() {},
                    sessionId,
                    trimmedSeason,
                    limit
            );
        }
        String trimmedCursor = afterCursor.trim();
        if (!PRISECTER_CURSOR_PATTERN.matcher(trimmedCursor).matches()) {
            throw new IllegalArgumentException("Cursor prisecter con formato inválido: " + trimmedCursor);
        }
        return get(
                "/users/history/league/{season}?limit={limit}&after=" + trimmedCursor,
                new TypeReference<TetraChannelEnvelope<TetraSeasonHistoryData>>() {},
                sessionId,
                trimmedSeason,
                limit
        );
    }

    public TetraChannelResponse<TetraGeneralStatsData> fetchGeneralStats(String sessionId) {
        return get(
                "/general/stats",
                new TypeReference<TetraChannelEnvelope<TetraGeneralStatsData>>() {},
                sessionId
        );
    }

    private <T> TetraChannelResponse<T> get(
            String uriTemplate,
            TypeReference<TetraChannelEnvelope<T>> typeRef,
            String sessionId,
            Object... uriVariables
    ) {
        rateLimiter.acquirePermit();
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(SESSION_HEADER, sessionId)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        HttpHeaders headers = response.getHeaders();

                        if (status.value() == 429) {
                            long retryAfter = parseRetryAfterSeconds(headers);
                            rateLimiter.recordUpstreamCooldown(retryAfter * 1000L);
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_UNAVAILABLE,
                                    "TETRA CHANNEL limitó temporalmente las solicitudes.",
                                    retryAfter
                            );
                        }

                        if (status.is5xxServerError()) {
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió un error interno del servidor."
                            );
                        }

                        String rawBody = readBody(response.getBody());
                        if (rawBody.isBlank()) {
                            if (status.value() == 404) {
                                throw new ApiProblemException(
                                        ErrorCode.PLAYER_NOT_FOUND,
                                        "El jugador solicitado no existe en TETRA CHANNEL."
                                );
                            }
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió una respuesta vacía."
                            );
                        }

                        TetraChannelEnvelope<T> envelope;
                        try {
                            envelope = objectMapper.readValue(rawBody, typeRef);
                        } catch (Exception parseEx) {
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió un payload JSON malformado o incompatible.",
                                    parseEx
                            );
                        }

                        if (envelope == null) {
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió un envoltorio nulo."
                            );
                        }

                        if (!envelope.success()) {
                            throw classifyUnsuccessfulEnvelope(status, headers, envelope);
                        }

                        if (status.isError()) {
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió un código de estado inconsistente con el envoltorio."
                            );
                        }

                        if (envelope.data() == null) {
                            throw new ApiProblemException(
                                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                                    "TETRA CHANNEL devolvió success=true pero sin el bloque data."
                            );
                        }

                        return new TetraChannelResponse<>(envelope.data(), envelope.cache(), rawBody);
                    });
        } catch (ApiProblemException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new ApiProblemException(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "No fue posible comunicarse con TETRA CHANNEL dentro del tiempo límite.",
                    ex
            );
        } catch (RestClientException ex) {
            if (ex.getCause() instanceof IOException) {
                throw new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "Fallo de red o tiempo de espera agotado al consultar TETRA CHANNEL.",
                        ex
                );
            }
            throw new ApiProblemException(
                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                    "Error al procesar la respuesta de TETRA CHANNEL.",
                    ex
            );
        }
    }

    private ApiProblemException classifyUnsuccessfulEnvelope(
            HttpStatusCode status,
            HttpHeaders headers,
            TetraChannelEnvelope<?> envelope
    ) {
        String rawMsg = (envelope.error() != null && envelope.error().msg() != null)
                ? envelope.error().msg()
                : "";
        String msgLower = rawMsg.toLowerCase(Locale.ROOT);

        if (isRateLimitErrorMessage(msgLower)) {
            long retryAfter = parseRetryAfterSeconds(headers);
            rateLimiter.recordUpstreamCooldown(retryAfter * 1000L);
            return new ApiProblemException(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "TETRA CHANNEL limitó temporalmente las solicitudes.",
                    retryAfter
            );
        }

        if (isUserNotFoundMessage(msgLower) || (status.value() == 404 && rawMsg.isBlank())) {
            return new ApiProblemException(
                    ErrorCode.PLAYER_NOT_FOUND,
                    "El jugador solicitado no existe en TETRA CHANNEL."
            );
        }

        return new ApiProblemException(
                ErrorCode.UPSTREAM_BAD_RESPONSE,
                "TETRA CHANNEL indicó un fallo al procesar la consulta."
        );
    }

    private static boolean isUserNotFoundMessage(String msgLower) {
        return msgLower.contains("no such user")
                || msgLower.contains("user not found")
                || msgLower.contains("cannot find user");
    }

    private static boolean isRateLimitErrorMessage(String msgLower) {
        return msgLower.contains("too many requests")
                || msgLower.contains("rate limit")
                || msgLower.contains("ratelimit")
                || msgLower.contains("flood");
    }

    private static long parseRetryAfterSeconds(HttpHeaders headers) {
        if (headers != null) {
            String headerVal = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (headerVal != null && !headerVal.isBlank()) {
                try {
                    long parsed = Long.parseLong(headerVal.trim());
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall back to default
                }
            }
        }
        return DEFAULT_UPSTREAM_RETRY_AFTER_SECONDS;
    }

    private static String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
