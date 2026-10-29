package com.tetrio.analytics.integration.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateRequest;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServiceClientTest {

    private MockRestServiceServer mockServer;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder, new ObjectMapper());
    }

    @Test
    void parsesSuccessfulPredictionWithShapExplanation() {
        String body = """
                {
                  "status": "ok",
                  "prediction": { "tr": 24750.5 },
                  "modelName": "tr-cross-ridge-baseline",
                  "modelVersion": "0.2.0",
                  "featureSchemaVersion": "tr-cross-v1",
                  "explanation": [
                    { "feature": "apm", "contribution": -10.5 },
                    { "feature": "vs", "contribution": 1150.2 }
                  ],
                  "explanationMethod": "shap"
                }
                """;

        mockServer.expect(requestTo("http://127.0.0.1:8000/v1/tr-estimate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Request-Id", "req-ai-123"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<AiTrEstimateResponse> response = client.estimateTr(
                new AiTrEstimateRequest(
                        "req-ai-123",
                        "tr-cross-v1",
                        Map.of("apm", 170.6, "pps", 2.73, "vs", 353.52, "gamesPlayed", 1711)
                )
        );

        assertThat(response).isPresent();
        assertThat(response.get().prediction().tr()).isEqualTo(24750.5);
        assertThat(response.get().explanationMethod()).isEqualTo("shap");
        assertThat(response.get().explanation()).hasSize(2);
    }

    @Test
    void returnsEmptyOptionalWhenAiServiceReportsModelUnavailable() {
        String problemBody = """
                {
                  "type": "/problems/model-unavailable",
                  "title": "Model unavailable",
                  "status": 503,
                  "detail": "No hay ningún modelo cargado.",
                  "code": "MODEL_UNAVAILABLE"
                }
                """;

        mockServer.expect(requestTo("http://127.0.0.1:8000/v1/tr-estimate"))
                .andRespond(withServiceUnavailable()
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problemBody));

        Optional<AiTrEstimateResponse> response = client.estimateTr(
                new AiTrEstimateRequest("req-1", "tr-cross-v1", Map.of("apm", 50.0))
        );

        assertThat(response).isEmpty();
    }

    @Test
    void maps422And5xxToConsistentErrorCodes() {
        String problem422 = """
                {
                  "status": 422,
                  "detail": "La variable requerida está ausente.",
                  "code": "PREDICTION_NOT_APPLICABLE"
                }
                """;

        mockServer.expect(requestTo("http://127.0.0.1:8000/v1/tr-estimate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem422));

        assertThatThrownBy(() -> client.estimateTr(
                new AiTrEstimateRequest("req-2", "tr-cross-v1", Map.of())
        ))
                .isInstanceOfSatisfying(ApiProblemException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.PREDICTION_NOT_APPLICABLE)
                );
    }
}
