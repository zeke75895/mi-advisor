package com.miadvisor.risk;

import com.miadvisor.auth.CurrentUser;
import com.miadvisor.risk.RiskDtos.CheckInResponse;
import com.miadvisor.risk.RiskDtos.PredictRequest;
import com.miadvisor.risk.RiskDtos.PredictResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** 204 when the course has no risk check yet. */
    @GetMapping("/api/courses/{courseId}/risk/latest")
    public ResponseEntity<PredictResponse> latestRisk(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return riskService.latestDetails(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 204 when the course has no check-in yet. */
    @GetMapping("/api/courses/{courseId}/check-in/latest")
    public ResponseEntity<CheckInResponse> latestCheckIn(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return riskService.latestCheckIn(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
