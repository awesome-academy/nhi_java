package demo.tripgo.exception;

// Ném khi tham số truy vấn có giá trị không hợp lệ (vd sort/category lạ); ánh xạ sang 400.
public class InvalidRequestParameterException extends RuntimeException {

    public InvalidRequestParameterException(String message) {
        super(message);
    }
}
