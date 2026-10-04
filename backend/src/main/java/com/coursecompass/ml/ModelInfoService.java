package com.coursecompass.ml;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/** Model metrics for the Model Insights page and the Responsible AI panel. Cached after the first success. */
@Service
public class ModelInfoService {

    private final MlClient mlClient;
    private final AtomicReference<JsonNode> cached = new AtomicReference<>();

    public ModelInfoService(MlClient mlClient) {
        this.mlClient = mlClient;
    }

    public JsonNode modelInfo() {
        JsonNode info = cached.get();
        if (info == null) {
            info = mlClient.modelInfo(); // throws MlServiceException (503/502) if the ML service is down
            cached.set(info);
        }
        return info;
    }
}
