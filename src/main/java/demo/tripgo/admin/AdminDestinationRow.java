package demo.tripgo.admin;

import java.time.LocalDateTime;

// Một dòng trong bảng điểm đến. tourCount cho admin biết trước có xoá được hay không.
public record AdminDestinationRow(
    Long id,
    String name,
    String slug,
    String image,
    long tourCount,
    LocalDateTime deletedAt
) {
}
