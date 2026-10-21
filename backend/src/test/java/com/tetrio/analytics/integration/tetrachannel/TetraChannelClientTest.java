package com.tetrio.analytics.integration.tetrachannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TetraChannelClientTest {

    private MockRestServiceServer mockServer;
    private TetraChannelRateLimiter rateLimiter;
    private TetraChannelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ch.tetr.io/api");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneOffset.UTC);
        rateLimiter = new TetraChannelRateLimiter(0L, 3000L, fixedClock, ms -> {});

        AppProperties props = AppProperties.of(
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.TetraChannelProperties("https://ch.tetr.io/api", 5000L, 0L, 3000L, "test-agent/0.2"),
                new AppProperties.CacheProperties(300L)
        );

        client = new TetraChannelClient(builder, rateLimiter, new ObjectMapper(), props);
    }

    @Test
    void fetchesUserSuccessfullyAndSendsSessionAndUserAgentHeaders() {
        String body = """
                {
                  "success": true,
                  "cache": { "status": "hit", "cached_at": 1792538893167, "cached_until": 1792539193167 },
                  "data": {
                    "_id": "5e9d4eec530bc13f9fdcd2c0",
                    "username": "5han",
                    "country": "JP"
                  }
                }
                """;

        mockServer.expect(requestTo("https://ch.tetr.io/api/users/5han"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Session-ID", "session-123"))
                .andExpect(header(HttpHeaders.USER_AGENT, "test-agent/0.2"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        TetraChannelResponse<TetraUserData> response = client.fetchUser("5han", "session-123");

        mockServer.verify();
        assertThat(response.data().id()).isEqualTo("5e9d4eec530bc13f9fdcd2c0");
        assertThat(response.data().username()).isEqualTo("5han");
        assertThat(response.cache().cachedUntil()).isEqualTo(1792539193167L);
    }

    @Test
    void mapsNonexistentUserSuccessFalseTo404PlayerNotFound() {
        String body = """
                {
                  "success": false,
                  "error": {
                    "msg": "No such user! | Either you mistyped something, or the account no longer exists."
                  }
                }
                """;

        mockServer.expect(requestTo("https://ch.tetr.io/api/users/ghostuser"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        assertThatThrownBy(() -> client.fetchUser("ghostuser", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.PLAYER_NOT_FOUND));
    }

    @Test
    void mapsProviderInternalErrorSuccessFalseTo502Not404() {
        String body = """
                {
                  "success": false,
                  "error": {
                    "msg": "Database shard temporarily unreachable"
                  }
                }
                """;

        mockServer.expect(requestTo("https://ch.tetr.io/api/users/5han"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        assertThatThrownBy(() -> client.fetchUser("5han", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_BAD_RESPONSE));
    }

    @Test
    void mapsUpstreamHttp5xxAndMalformedJsonTo502UpstreamBadResponse() {
        mockServer.expect(requestTo("https://ch.tetr.io/api/users/5han"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>502 Bad Gateway</html>"));

        assertThatThrownBy(() -> client.fetchUser("5han", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_BAD_RESPONSE));
    }

    @Test
    void mapsUpstreamHttp429To503UpstreamUnavailableWithRetryAfterAndCooldown() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "12");

        mockServer.expect(requestTo("https://ch.tetr.io/api/users/5han"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> client.fetchUser("5han", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
                    assertThat(apiEx.retryAfterSeconds()).isEqualTo(12L);
                });

        // Subsequent call should fail immediately due to active upstream cooldown without hitting server
        assertThatThrownBy(() -> client.fetchUser("5han", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void mapsNetworkTimeoutTo503UpstreamUnavailable() {
        mockServer.expect(requestTo("https://ch.tetr.io/api/users/5han"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> client.fetchUser("5han", "session-123"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void fetchesLeagueLeaderboardPageWithPrisecterCursorAndPreservesRawJson() {
        String body = """
                {
                  "success": true,
                  "cache": { "status": "hit", "cached_at": 1792538893167, "cached_until": 1792539193167 },
                  "data": {
                    "entries": [
                      {
                        "_id": "5e9d4eec530bc13f9fdcd2c0",
                        "username": "5han",
                        "country": "JP",
                        "league": {
                          "gamesplayed": 1711,
                          "gameswon": 1471,
                          "tr": 24863.06,
                          "rank": "x+"
                        },
                        "p": { "pri": 24863.06, "sec": 0, "ter": 0 }
                      }
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo("https://ch.tetr.io/api/users/by/league?limit=100&after=24900:0:0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Session-ID", "session-leaderboard"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        TetraChannelResponse<TetraLeaderboardData> response =
                client.fetchLeagueLeaderboardPage(100, "24900:0:0", "session-leaderboard");

        mockServer.verify();
        assertThat(response.rawJson()).contains("\"5han\"");
        assertThat(response.data().entries()).hasSize(1);
        assertThat(response.data().entries().getFirst().p().toCursorString()).isEqualTo("24863.06:0:0");
    }
}
