package demo.tripgo.dto.response;

// Kết quả các thao tác tạo/huỷ đơn, kèm message như register/login/review.
public record BookingActionResponse(
    String message,
    BookingResponse booking
) {
}
