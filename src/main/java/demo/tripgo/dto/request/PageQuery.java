package demo.tripgo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Tham số phân trang dùng chung cho mọi endpoint list (page 1-based, limit có trần).
// Endpoint chỉ cần phân trang thì dùng thẳng record này; endpoint có thêm filter (vd TourListRequest)
// giữ field phẳng để bind query param nhưng tái dùng hằng số + helper static ở đây (tránh copy logic).
public record PageQuery(
    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "limit must be >= 1")
    @Max(value = MAX_LIMIT, message = "limit must be <= " + MAX_LIMIT)
    Integer limit
) {
    public static final int MAX_LIMIT = 50;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 10;

    public int pageOrDefault() {
        return pageOrDefault(page);
    }

    public int limitOrDefault() {
        return limitOrDefault(limit);
    }

    public static int pageOrDefault(Integer page) {
        return page == null ? DEFAULT_PAGE : page;
    }

    public static int limitOrDefault(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
