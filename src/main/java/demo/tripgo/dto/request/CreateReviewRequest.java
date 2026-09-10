package demo.tripgo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dữ liệu tạo đánh giá. rating bắt buộc 1..5; comment tuỳ chọn nhưng giới hạn độ dài.
public record CreateReviewRequest(
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    Integer rating,

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    String comment
) {
}
