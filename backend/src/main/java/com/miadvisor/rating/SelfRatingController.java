package com.miadvisor.rating;

import com.miadvisor.auth.CurrentUser;
import com.miadvisor.rating.SelfRatingDtos.RatingRequest;
import com.miadvisor.rating.SelfRatingDtos.RatingResponse;
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
public class SelfRatingController {

    private final SelfRatingService ratingService;

    public SelfRatingController(SelfRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/api/items/{itemId}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse rate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId, @Valid @RequestBody RatingRequest request) {
        return ratingService.rate(CurrentUser.id(jwt), itemId, request.rating());
    }
}
