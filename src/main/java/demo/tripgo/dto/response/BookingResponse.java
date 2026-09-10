package demo.tripgo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Đơn đặt tour đầy đủ cho client.
public record BookingResponse(
    Long id,
    String code,
    String status,
    Long tourId,
    String tourTitle,
    LocalDate date,
    int adults,
    int children,
    BigDecimal totalPrice,
    ContactResponse contact,
    LocalDateTime createdAt
) {
}
