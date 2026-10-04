package com.coursecompass.ml;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelInfoController {

    private final ModelInfoService modelInfoService;

    public ModelInfoController(ModelInfoService modelInfoService) {
        this.modelInfoService = modelInfoService;
    }

    @GetMapping("/api/model-info")
    public JsonNode modelInfo() {
        return modelInfoService.modelInfo();
    }
}
