package demo.tripgo.repository;

import demo.tripgo.dto.request.TourFilter;
import demo.tripgo.entity.Tour;
import demo.tripgo.service.SearchText;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

// Dựng điều kiện lọc động cho danh sách tour. Mỗi filter chỉ thêm predicate khi có giá trị,
// nên toàn bộ việc lọc chạy ở DB (không tải hết rồi lọc trong bộ nhớ).
public final class TourSpecifications {

    private TourSpecifications() {
    }

    public static Specification<Tour> withFilter(TourFilter filter) {
        return (root, query, cb) -> {
            // destination NOT NULL nên inner join không làm rớt tour nào. Ở truy vấn dữ liệu dùng
            // fetch để nạp luôn điểm đến (tránh N+1 khi map card); ở truy vấn count dùng join thường.
            boolean isCount = query.getResultType() != null
                && (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType()));
            Join<?, ?> destination = isCount
                ? root.join("destination", JoinType.INNER)
                : (Join<?, ?>) root.fetch("destination", JoinType.INNER);
            // category cũng NOT NULL và cũng được TourMapper đọc (lấy slug) -> phải fetch y hệt
            // destination, nếu không mỗi tour sẽ sinh thêm một truy vấn (N+1).
            Join<?, ?> category = isCount
                ? root.join("category", JoinType.INNER)
                : (Join<?, ?>) root.fetch("category", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            // Luôn loại tour đã xoá mềm khỏi danh sách công khai, không phụ thuộc filter nào.
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (hasText(filter.q())) {
                // Tìm trên cột searchText (dạng không dấu của title + description) để gõ "da nang"
                // vẫn ra "Đà Nẵng". Từ khoá chuẩn hoá bằng ĐÚNG hàm đã dùng lúc lưu, và vẫn escape
                // % _ để chúng là ký tự thường chứ không phải wildcard.
                predicates.add(cb.like(
                    root.get("searchText"),
                    SearchText.likePattern(filter.q()),
                    SearchText.LIKE_ESCAPE_CHAR));
            }
            if (hasText(filter.destination())) {
                predicates.add(cb.equal(destination.get("slug"), filter.destination().trim()));
            }
            if (hasText(filter.categorySlug())) {
                predicates.add(cb.equal(category.get("slug"), filter.categorySlug().trim()));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }
            if (filter.duration() != null) {
                predicates.add(cb.equal(root.get("durationDays"), filter.duration()));
            }
            if (filter.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ratingAvg"), filter.minRating()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
