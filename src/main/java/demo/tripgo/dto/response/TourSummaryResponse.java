package demo.tripgo.dto.response;

import demo.tripgo.entity.TourCategory;

import java.math.BigDecimal;

// Thông tin gọn cho card ở danh sách; chỉ có thumbnail thay vì cả gallery ảnh.
public record TourSummaryResponse(
    Long id,
    String title,
    DestinationResponse destination,
    TourCategory category,
    int durationDays,
    BigDecimal price,
    BigDecimal discountPrice,
    double ratingAvg,
    int reviewCount,
    String thumbnailUrl
) {
}
