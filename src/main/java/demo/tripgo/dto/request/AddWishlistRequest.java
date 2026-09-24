package demo.tripgo.dto.request;

import jakarta.validation.constraints.NotNull;

// Body thêm tour vào wishlist (hợp đồng 6.6).
public record AddWishlistRequest(
    @NotNull(message = "tourId không được để trống")
    Long tourId
) {
}
