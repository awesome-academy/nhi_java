package demo.tripgo.exception;

// Ném khi huỷ một đơn đã huỷ trước đó; ánh xạ sang 409.
public class BookingAlreadyCancelledException extends RuntimeException {

    public BookingAlreadyCancelledException(String code) {
        super("Booking " + code + " is already cancelled");
    }
}
