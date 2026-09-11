package demo.tripgo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Tham số phân trang dùng chung cho mọi endpoint list: ?page=&size= (page 1-based, size có trần).
// Endpoint chỉ phân trang thì dùng thẳng record này; endpoint có thêm filter (vd TourListRequest)
// giữ field phẳng để bind query param nhưng tái dùng hằng số + helper static ở đây (tránh copy logic).
public record PageQuery(
    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = MAX_SIZE, message = "size must be <= " + MAX_SIZE)
    Integer size
) {
    public static final int MAX_SIZE = 50;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;

    public int pageOrDefault() {
        return pageOrDefault(page);
    }

    public int sizeOrDefault() {
        return sizeOrDefault(size);
    }

    public static int pageOrDefault(Integer page) {
        return page == null ? DEFAULT_PAGE : page;
    }

    public static int sizeOrDefault(Integer size) {
        return size == null ? DEFAULT_SIZE : size;
    }
}
