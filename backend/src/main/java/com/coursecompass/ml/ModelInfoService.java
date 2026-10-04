package com.coursecompass.ml;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Model metrics for the Model Insights page and the Responsible AI panel. Cached for a short time so
 * a retrained model shows up without restarting the backend; if the ML service is briefly down, the
 * last good copy is served instead of an error.
 */
@Service
public class ModelInfoService {

    private static final Logger log = LoggerFactory.getLogger(ModelInfoService.class);

    private record Cached(JsonNode info, Instant fetchedAt) {}

    private final MlClient mlClient;
    private final Duration ttl;
    private final Clock clock;
    private volatile Cached cached;

    public ModelInfoService(MlClient mlClient, @Value("${ml.model-info-ttl:PT5M}") Duration ttl, Clock clock) {
        this.mlClient = mlClient;
        this.ttl = ttl;
        this.clock = clock;
    }

    public JsonNode modelInfo() {
        Cached current = cached;
        Instant now = clock.instant();
        if (current != null && current.fetchedAt().plus(ttl).isAfter(now)) {
            return current.info();
        }
        try {
            JsonNode fresh = mlClient.modelInfo();
            cached = new Cached(fresh, now);
            return fresh;
        } catch (MlServiceException e) {
            if (current == null) {
                throw e; // nothing to fall back to
            }
            log.warn("ML service unavailable, serving model info cached at {}", current.fetchedAt());
            return current.info();
        }
    }
}
