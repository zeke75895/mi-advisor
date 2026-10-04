package com.coursecompass.ml;

import com.coursecompass.ml.MlDtos.MlPredictRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.coursecompass.ml.MlDtos.MlPredictResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** HTTP client for the FastAPI ML service's POST /predict. */
@Component
public class MlClient {

    private final RestClient restClient;

    public MlClient(RestClient.Builder builder, @Value("${ml.service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /** GET /model-info: test-set metrics, baseline, importances and tree leaves. */
    public JsonNode modelInfo() {
        try {
            return restClient.get()
                    .uri("/model-info")
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MlServiceException(HttpStatus.BAD_GATEWAY, "ML service error");
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML service is unavailable");
        }
    }

    public MlPredictResponse predict(MlPredictRequest request) {
        try {
            return restClient.post()
                    .uri("/predict")
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new MlServiceException(
                                HttpStatus.BAD_REQUEST, "ML service rejected the features: " + body);
                    })
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new MlServiceException(HttpStatus.BAD_GATEWAY, "ML service error");
                    })
                    .body(MlPredictResponse.class);
        } catch (ResourceAccessException e) {
            throw new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML service is unavailable");
        }
    }
}
