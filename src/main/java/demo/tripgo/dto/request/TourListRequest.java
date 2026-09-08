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
    @Max(value = MAX_LIMIT, message = "limit must be <= " + MAX_LIMIT)
    Integer limit
) {
    public static final int MAX_LIMIT = 50;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LIMIT = 10;

    // Không truyền thì dùng mặc định; giá trị sai đã bị @Min/@Max chặn (→ 400) trước khi tới đây.
    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int limitOrDefault() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
