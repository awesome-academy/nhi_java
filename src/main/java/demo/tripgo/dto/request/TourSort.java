package demo.tripgo.dto.request;

import demo.tripgo.exception.InvalidRequestParameterException;
import org.springframework.data.domain.Sort;

import java.util.Locale;

// Các lựa chọn sắp xếp cho danh sách tour, ánh xạ sang Sort của Spring Data để DB sắp xếp.
public enum TourSort {
    NEWEST("newest", Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC("price_asc", Sort.by(Sort.Direction.ASC, "price")),
    PRICE_DESC("price_desc", Sort.by(Sort.Direction.DESC, "price")),
    RATING_DESC("rating_desc", Sort.by(Sort.Direction.DESC, "ratingAvg"));

    private final String param;
    private final Sort sort;

    TourSort(String param, Sort sort) {
        this.param = param;
        this.sort = sort;
    }

    public Sort toSort() {
        // Thêm id làm tie-breaker để thứ tự ổn định giữa các trang khi giá/đánh giá trùng nhau.
        return sort.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    // Trả về NEWEST khi không truyền
    public static TourSort from(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TourSort option : values()) {
            if (option.param.equals(normalized)) {
                return option;
            }
        }

        throw new InvalidRequestParameterException(
            "Invalid sort value: " + value + ". Allowed: newest, price_asc, price_desc, rating_desc");
    }
}
