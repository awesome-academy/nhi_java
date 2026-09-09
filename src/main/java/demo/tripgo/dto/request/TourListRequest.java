package demo.tripgo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record TourListRequest(
    String q,
    String destination,
    String category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer duration,
    Double rating,
    String sort,

    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "limit must be >= 1")
    @Max(value = PageQuery.MAX_LIMIT, message = "limit must be <= " + PageQuery.MAX_LIMIT)
    Integer limit
) {
    // Tái dùng logic phân trang chung; không truyền thì dùng mặc định (giá trị sai đã bị @Min/@Max chặn).
    public int pageOrDefault() {
        return PageQuery.pageOrDefault(page);
    }

    public int limitOrDefault() {
        return PageQuery.limitOrDefault(limit);
    }
}
