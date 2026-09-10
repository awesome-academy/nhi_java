package demo.tripgo.exception;

// Ném khi đơn không hợp lệ về nghiệp vụ (vd ngày không có chuyến khởi hành); ánh xạ sang 422.
public class InvalidBookingRequestException extends RuntimeException {

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
