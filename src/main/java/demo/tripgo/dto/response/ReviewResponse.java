package demo.tripgo.dto.response;

import java.time.LocalDate;

// Một đánh giá theo hợp đồng 6.3: người đánh giá là object lồng { name },
// createdAt chỉ lấy phần ngày (giống cách BookingResponse xuất createdAt).
public record ReviewResponse(
    Long id,
    Reviewer user,
    int rating,
    String comment,
    LocalDate createdAt
) {
    public record Reviewer(String name) {
    }
}
