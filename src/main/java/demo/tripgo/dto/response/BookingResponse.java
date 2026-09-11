package demo.tripgo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// Đơn đặt tour trả cho client (POST tạo & GET chi tiết) theo hợp đồng 6.5.
public record BookingResponse(
    Long id,
    String code,
    Long tourId,
    LocalDate date,
    int adults,
    int children,
    BigDecimal totalPrice,
    String status,
    LocalDate createdAt
) {
}
