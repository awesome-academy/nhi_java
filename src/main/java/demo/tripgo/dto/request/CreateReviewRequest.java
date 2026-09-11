package demo.tripgo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Dữ liệu tạo đánh giá. rating bắt buộc 1..5; comment tuỳ chọn nhưng giới hạn độ dài.
public record CreateReviewRequest(
    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 1, message = "Điểm đánh giá phải từ 1 đến 5")
    @Max(value = 5, message = "Điểm đánh giá phải từ 1 đến 5")
    Integer rating,

    @Size(max = 2000, message = "Nội dung đánh giá không được vượt quá 2000 ký tự")
    String comment
) {
}
