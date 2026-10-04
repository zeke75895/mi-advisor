package com.miadvisor.briefing;

import com.miadvisor.auth.CurrentUser;
import com.miadvisor.briefing.BriefingDtos.BriefingResponse;
import com.miadvisor.briefing.BriefingDtos.FeaturesResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BriefingController {

    private final BriefingService briefingService;

    public BriefingController(BriefingService briefingService) {
        this.briefingService = briefingService;
    }

    /** Lets the frontend hide the Listen button when no ElevenLabs key is configured. */
    @GetMapping("/api/features")
    public FeaturesResponse features() {
        return new FeaturesResponse(briefingService.isAvailable());
    }

    @PostMapping("/api/courses/{courseId}/briefing")
    public BriefingResponse briefing(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return briefingService.brief(CurrentUser.id(jwt), courseId);
    }
}
