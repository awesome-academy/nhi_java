package demo.tripgo.exception;

// Ném khi không tìm thấy tài nguyên; ánh xạ sang 404 trong GlobalExceptionHandler.
// resource là tên tài nguyên tiếng Việt (vd "tour", "đơn đặt tour") để ghép thẳng vào message.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource) {
        super("Không tìm thấy " + resource);
    }
}
