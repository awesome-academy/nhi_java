package demo.tripgo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// Item trong danh sách đơn của tôi (GET /bookings) — kèm tour gọn { title, thumbnail }.
public record BookingSummaryResponse(
    Long id,
    String code,
    TourBrief tour,
    LocalDate date,
    BigDecimal totalPrice,
    String status
) {
    public record TourBrief(String title, String thumbnail) {
    }
}
