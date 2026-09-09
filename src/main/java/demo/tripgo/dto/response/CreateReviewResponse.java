package demo.tripgo.dto.response;

// Kết quả tạo đánh giá, kèm message giống RegisterResponse/LoginResponse.
public record CreateReviewResponse(
    String message,
    ReviewResponse review
) {
}
