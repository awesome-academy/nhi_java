package demo.tripgo.dto.response;

import java.math.BigDecimal;

// Thông tin gọn cho card ở danh sách; chỉ có thumbnail thay vì cả gallery ảnh.
// destination trả về tên điểm đến dạng chuỗi (card chỉ cần hiển thị, không cần id/slug);
// màn chi tiết dùng TourDetailResponse mới trả object đầy đủ.
public record TourSummaryResponse(
    Long id,
    String slug,
    String title,
    String destination,
    String category,
    int durationDays,
    BigDecimal price,
    BigDecimal discountPrice,
    double rating,
    int reviewCount,
    String thumbnail
) {
}
