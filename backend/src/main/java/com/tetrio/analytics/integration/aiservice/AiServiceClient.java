package com.tetrio.analytics.integration.aiservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tetrio.analytics.integration.aiservice.dto.AiModelCardResponse;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateRequest;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiServiceClient {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(
            @Qualifier("aiServiceRestClientBuilder") RestClient.Builder aiServiceRestClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.restClient = aiServiceRestClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public Optional<AiTrEstimateResponse> estimateTr(AiTrEstimateRequest requestPayload) {
        try {
            return restClient.post()
                    .uri("/v1/tr-estimate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .header(REQUEST_ID_HEADER, requestPayload.requestId())
                    .body(requestPayload)
                    .exchange((req, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        String rawBody = readBody(res.getBody());

                        if (status.is2xxSuccessful()) {
                            if (rawBody.isBlank()) {
                                throw new ApiProblemException(
                                        ErrorCode.AI_SERVICE_UNAVAILABLE,
                                        "El servicio interno de IA devolvió una respuesta vacía."
                                );
                            }
                            AiTrEstimateResponse parsed = objectMapper.readValue(rawBody, AiTrEstimateResponse.class);
                            return Optional.of(parsed);
                        }

                        JsonNode problemNode = parseJsonSafe(rawBody);
                        String problemCode = problemNode != null && problemNode.hasNonNull("code")
                                ? problemNode.get("code").asText()
                                : "";
                        String detail = problemNode != null && problemNode.hasNonNull("detail")
                                ? problemNode.get("detail").asText()
                                : "El servicio interno de IA rechazó o no pudo completar la inferencia.";

                        if (status.value() == 503 && "MODEL_UNAVAILABLE".equals(problemCode)) {
                            return Optional.empty();
                        }

                        if (status.value() == 422 && "PREDICTION_NOT_APPLICABLE".equals(problemCode)) {
                            throw new ApiProblemException(ErrorCode.PREDICTION_NOT_APPLICABLE, detail);
                        }

                        if (status.value() == 422) {
                            throw new ApiProblemException(ErrorCode.VALIDATION_FAILED, detail);
                        }

                        throw new ApiProblemException(
                                ErrorCode.AI_SERVICE_UNAVAILABLE,
                                "El servicio interno de IA no está disponible o devolvió un error inesperado."
                        );
                    });
        } catch (ApiProblemException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new ApiProblemException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "El servicio interno de IA no responde o excedió el tiempo de espera.",
                    ex
            );
        } catch (RestClientException ex) {
            throw new ApiProblemException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "No fue posible comunicarse con el servicio interno de IA.",
                    ex
            );
        }
    }

    public Optional<AiModelCardResponse> fetchModelCard(String requestId) {
        try {
            return restClient.get()
                    .uri("/v1/model-card")
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                    .header(REQUEST_ID_HEADER, requestId)
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            return Optional.empty();
                        }
                        String rawBody = readBody(res.getBody());
                        if (rawBody.isBlank()) {
                            return Optional.empty();
                        }
                        return Optional.of(objectMapper.readValue(rawBody, AiModelCardResponse.class));
                    });
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private JsonNode parseJsonSafe(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ignored) {
            return null;
        }
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
