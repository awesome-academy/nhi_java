package demo.tripgo.exception;

// Ném khi ngày khởi hành không còn đủ chỗ; ánh xạ sang 409.
public class SoldOutException extends RuntimeException {

    public SoldOutException(String message) {
        super(message);
    }
}
