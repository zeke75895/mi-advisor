package com.coursecompass.item;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.item.GradedItemDtos.GradedItemRequest;
import com.coursecompass.item.GradedItemDtos.GradedItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/{courseId}/items")
public class GradedItemController {

    private final GradedItemService itemService;

    public GradedItemController(GradedItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<GradedItemResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long courseId) {
        return itemService.list(CurrentUser.id(jwt), courseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GradedItemResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long courseId,
            @Valid @RequestBody GradedItemRequest request) {
        return itemService.create(CurrentUser.id(jwt), courseId, request);
    }
}
