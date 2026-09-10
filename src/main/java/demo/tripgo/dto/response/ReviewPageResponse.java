package demo.tripgo.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Danh sách đánh giá phân trang kèm điểm trung bình của tour ({ data, total, page, size, averageRating }).
public record ReviewPageResponse(
    List<ReviewResponse> data,
    long total,
    int page,
    int size,
    double averageRating
) {
    public static ReviewPageResponse of(Page<ReviewResponse> page, double averageRating) {
        return new ReviewPageResponse(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber() + 1,
            page.getSize(),
            averageRating
        );
    }
}
