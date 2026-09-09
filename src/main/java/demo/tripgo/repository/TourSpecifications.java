package demo.tripgo.repository;

import demo.tripgo.dto.request.TourFilter;
import demo.tripgo.entity.Tour;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Dựng điều kiện lọc động cho danh sách tour. Mỗi filter chỉ thêm predicate khi có giá trị,
// nên toàn bộ việc lọc chạy ở DB (không tải hết rồi lọc trong bộ nhớ).
public final class TourSpecifications {

    // Ký tự escape dùng cho các pattern LIKE khi search theo từ khoá.
    private static final char LIKE_ESCAPE_CHAR = '\\';

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

            List<Predicate> predicates = new ArrayList<>();

            if (hasText(filter.q())) {
                // Escape \, %, _ để chúng được hiểu là ký tự literal, không phải wildcard của LIKE.
                String escaped = escapeLike(filter.q().trim().toLowerCase(Locale.ROOT));
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE_CHAR),
                    cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE_CHAR)
                ));
            }
            if (hasText(filter.destination())) {
                predicates.add(cb.equal(destination.get("slug"), filter.destination().trim()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
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

    // Escape ký tự đặc biệt của LIKE. Phải xử lý '\' trước để không escape lại chính escape char.
    private static String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
