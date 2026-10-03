package com.coursecompass.risk;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.risk.RiskDtos.PredictRequest;
import com.coursecompass.risk.RiskDtos.PredictResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping("/api/courses/{courseId}/predict")
    @ResponseStatus(HttpStatus.CREATED)
    public PredictResponse predict(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId, @Valid @RequestBody PredictRequest request) {
        return riskService.predict(CurrentUser.id(jwt), courseId, request);
    }
}
