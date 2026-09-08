package demo.tripgo.dto.request;

import demo.tripgo.entity.TourCategory;

import java.math.BigDecimal;

// Tập điều kiện lọc đã được chuẩn hoá, dùng để dựng JPA Specification.
public record TourFilter(
    String q,
    String destination,
    TourCategory category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer duration,
    Double minRating
) {
}
