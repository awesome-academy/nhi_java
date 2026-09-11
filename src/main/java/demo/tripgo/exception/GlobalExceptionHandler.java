package demo.tripgo.exception;

import demo.tripgo.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

// Trả lỗi theo định dạng thống nhất { "error": { "code", "message", "fields"? } }.
// code = tên HttpStatus (vd "NOT_FOUND"); fields chỉ có ở lỗi validate. Không lộ stack trace/secret.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return build(HttpStatus.CONFLICT, "Data conflicts with existing records or database constraints");
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, ReviewAlreadyExistsException.class,
        SoldOutException.class, BookingAlreadyCancelledException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // Đơn không hợp lệ về nghiệp vụ (vd ngày không có chuyến) → 422.
    @ExceptionHandler(InvalidBookingRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingRequest(InvalidBookingRequestException exception) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    // Giá trị/kiểu tham số truy vấn sai (sort/category lạ, path variable sai kiểu) → 400.
    @ExceptionHandler(InvalidRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(InvalidRequestParameterException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String type = exception.getRequiredType() != null
            ? exception.getRequiredType().getSimpleName()
            : "the expected type";
        return build(HttpStatus.BAD_REQUEST,
            "Parameter '" + exception.getName() + "' must be of type " + type);
    }

    // Lỗi bean-validation trên body (@Valid) → 422 kèm map trường lỗi.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> fields.putIfAbsent(error.getField(), fieldErrorMessage(error)));
        return buildValidation(fields);
    }

    // Lỗi bean-validation trên tham số (@Validated) → 422 kèm map trường lỗi.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            // propertyPath dạng "method.param"; chỉ giữ tên param cho gọn.
            String param = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fields.putIfAbsent(param, violation.getMessage());
        });
        return buildValidation(fields);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.name(), message));
    }

    private ResponseEntity<ErrorResponse> buildValidation(Map<String, String> fields) {
        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ErrorResponse.of(status.name(), "Validation failed", fields));
    }

    // Lỗi ép kiểu khi bind (vd minPrice=abc): thay message nội bộ của Spring bằng "must be of type X".
    private String fieldErrorMessage(FieldError error) {
        if ("typeMismatch".equals(error.getCode())) {
            return "must be of type " + requiredTypeName(error);
        }
        return error.getDefaultMessage();
    }

    private String requiredTypeName(FieldError error) {
        try {
            TypeMismatchException cause = error.unwrap(TypeMismatchException.class);
            if (cause.getRequiredType() != null) {
                return cause.getRequiredType().getSimpleName();
            }
        } catch (RuntimeException ignored) {
            // Không lấy được kiểu yêu cầu thì dùng mô tả chung bên dưới.
        }
        return "the expected type";
    }
}
