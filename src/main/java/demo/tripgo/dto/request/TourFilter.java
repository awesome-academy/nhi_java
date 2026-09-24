package demo.tripgo.dto.request;


import java.math.BigDecimal;

// Tập điều kiện lọc đã được chuẩn hoá, dùng để dựng JPA Specification.
public record TourFilter(
    String q,
    String destination,
    String categorySlug,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer duration,
    Double minRating
) {
}
