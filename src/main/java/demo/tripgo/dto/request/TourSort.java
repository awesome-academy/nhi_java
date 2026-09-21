package demo.tripgo.dto.request;

import demo.tripgo.exception.InvalidRequestParameterException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;

// Các lựa chọn sắp xếp cho danh sách tour, ánh xạ sang Sort của Spring Data để DB sắp xếp.
// Mỗi lựa chọn có thể nhận nhiều tên: tên đầu là tên chính thức theo hợp đồng 6.3, các tên
// sau là bí danh giữ tương thích ngược (rating_desc từng là tên duy nhất được chấp nhận).
public enum TourSort {
    NEWEST(List.of("newest"), Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC(List.of("price_asc"), Sort.by(Sort.Direction.ASC, "price")),
    PRICE_DESC(List.of("price_desc"), Sort.by(Sort.Direction.DESC, "price")),
    // "tour nổi bật" = đánh giá cao nhất trước.
    RATING(List.of("rating", "rating_desc"), Sort.by(Sort.Direction.DESC, "ratingAvg"));

    private final List<String> params;
    private final Sort sort;

    TourSort(List<String> params, Sort sort) {
        this.params = params;
        this.sort = sort;
    }

    public String getParam() {
        return params.get(0);
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
            if (option.params.contains(normalized)) {
                return option;
            }
        }

        throw new InvalidRequestParameterException(
            "Giá trị sắp xếp không hợp lệ: " + value + ". Cho phép: "
                + java.util.Arrays.stream(values()).map(TourSort::getParam).toList());
    }
}
