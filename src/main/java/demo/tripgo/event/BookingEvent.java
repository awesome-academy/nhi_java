package demo.tripgo.event;

import demo.tripgo.entity.Booking;
import demo.tripgo.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Một việc vừa xảy ra với đơn đặt. Là bản sao dữ liệu tại thời điểm đó, KHÔNG giữ entity:
// người nhận xử lý ở luồng khác và ngoài transaction, nơi entity lazy đã hết dùng được.
public record BookingEvent(
    Kind kind,
    Long bookingId,
    String code,
    String customerName,
    String tourTitle,
    BigDecimal totalPrice,
    BookingStatus status,
    LocalDateTime at
) {
    public enum Kind {
        CREATED,
        CONFIRMED,
        CANCELLED
    }

    public static BookingEvent of(Kind kind, Booking booking) {
        return new BookingEvent(
            kind,
            booking.getId(),
            booking.getCode(),
            booking.getContact() == null ? null : booking.getContact().getFullName(),
            booking.getTour().getTitle(),
            booking.getTotalPrice(),
            booking.getStatus(),
            LocalDateTime.now());
    }

    // Câu hiển thị trên thông báo. Đặt cạnh sự kiện để nơi nào gửi đi cũng nói giống nhau.
    public String message() {
        return switch (kind) {
            case CREATED -> "Đơn mới %s — %s".formatted(code, tourTitle);
            case CONFIRMED -> "Đã xác nhận đơn %s".formatted(code);
            case CANCELLED -> "Đã huỷ đơn %s".formatted(code);
        };
    }
}
