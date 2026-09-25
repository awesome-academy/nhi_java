package demo.tripgo.admin;

import demo.tripgo.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

// Một dòng trong bảng đơn đặt. Map trong transaction để template không chạm vào quan hệ lazy.
public record AdminBookingRow(
    Long id,
    String code,
    String customerName,
    String customerEmail,
    String tourTitle,
    LocalDate departureDate,
    int guests,
    BigDecimal totalPrice,
    BookingStatus status
) {
    // Chỉ đơn đang chờ mới đổi được trạng thái (hợp đồng: pending -> confirmed/cancelled).
    public boolean isActionable() {
        return status == BookingStatus.PENDING;
    }
}
