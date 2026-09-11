package demo.tripgo.controller;

import demo.tripgo.dto.request.AddWishlistRequest;
import demo.tripgo.dto.response.WishlistResponse;
import demo.tripgo.entity.User;
import demo.tripgo.security.AuthUtils;
import demo.tripgo.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Wishlist luôn là của user trong JWT; không nhận userId từ client.
@RestController
@RequestMapping("/wishlist")
@PreAuthorize("isAuthenticated()")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<WishlistResponse> getWishlist(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(wishlistService.getWishlist(AuthUtils.requireUser(user)));
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> addTour(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody AddWishlistRequest request
    ) {
        return ResponseEntity.ok(wishlistService.addTour(AuthUtils.requireUser(user), request.tourId()));
    }

    @DeleteMapping("/{tourId}")
    public ResponseEntity<WishlistResponse> removeTour(
        @AuthenticationPrincipal User user,
        @PathVariable Long tourId
    ) {
        return ResponseEntity.ok(wishlistService.removeTour(AuthUtils.requireUser(user), tourId));
    }
}
