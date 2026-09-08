package demo.tripgo.exception;

// Ném khi không tìm thấy tài nguyên theo định danh; ánh xạ sang 404 trong GlobalExceptionHandler.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
