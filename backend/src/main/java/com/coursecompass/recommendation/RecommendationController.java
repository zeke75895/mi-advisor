package com.coursecompass.recommendation;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.recommendation.RecommendationDtos.ProjectionResponse;
import com.coursecompass.recommendation.RecommendationDtos.RecommendationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/api/courses/{courseId}/projection")
    public ProjectionResponse projection(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return recommendationService.projection(CurrentUser.id(jwt), courseId);
    }

    /** 204 until the course has a risk check and at least one graded or rated item. */
    @GetMapping("/api/courses/{courseId}/recommendation")
    public ResponseEntity<RecommendationResponse> recommendation(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return recommendationService.recommend(CurrentUser.id(jwt), courseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
