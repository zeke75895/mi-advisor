package com.coursecompass.recommendation;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.recommendation.RecommendationDtos.RecommendationResponse;
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

    @GetMapping("/api/courses/{courseId}/recommendation")
    public RecommendationResponse recommendation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return recommendationService.recommend(CurrentUser.id(jwt), courseId);
    }
}
