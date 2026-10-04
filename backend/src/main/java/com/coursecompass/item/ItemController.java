package com.coursecompass.item;

import com.coursecompass.auth.CurrentUser;
import com.coursecompass.item.GradedItemDtos.GradedItemRequest;
import com.coursecompass.item.GradedItemDtos.GradedItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Edit or delete a single graded item (e.g. enter the grade once it's back). */
@RestController
@RequestMapping("/api/items/{itemId}")
public class ItemController {

    private final GradedItemService itemService;

    public ItemController(GradedItemService itemService) {
        this.itemService = itemService;
    }

    @PutMapping
    public GradedItemResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId, @Valid @RequestBody GradedItemRequest request) {
        return itemService.update(CurrentUser.id(jwt), itemId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        itemService.delete(CurrentUser.id(jwt), itemId);
    }
}
