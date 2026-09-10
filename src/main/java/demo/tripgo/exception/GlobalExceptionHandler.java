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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Data conflicts with existing records or database constraints",
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleReviewAlreadyExists(ReviewAlreadyExistsException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Lỗi bean-validation (@Valid) → 422 Unprocessable Entity: body/tham số đúng cú pháp nhưng sai luật.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> errors.putIfAbsent(error.getField(), fieldErrorMessage(error)));

        ErrorResponse response = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Validation failed",
            errors,
            LocalDateTime.now()
        );
        return ResponseEntity.unprocessableEntity().body(response);
    }

    // Với lỗi ép kiểu khi bind (vd minPrice=abc) thì defaultMessage là message nội bộ của Spring;
    // thay bằng thông báo gọn "must be of type X" cho client. Lỗi bean-validation giữ nguyên message tự viết.
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Vi phạm ràng buộc trên query param (bean-validation cấp tham số) → 422 như các lỗi validate khác.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            // propertyPath dạng "method.param"; chỉ giữ tên param cho gọn.
            String param = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
            errors.putIfAbsent(param, violation.getMessage());
        });
        ErrorResponse response = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Validation failed",
            errors,
            LocalDateTime.now()
        );
        return ResponseEntity.unprocessableEntity().body(response);
    }

    // Đơn không hợp lệ về nghiệp vụ (vd ngày không có chuyến) → 422.
    @ExceptionHandler(InvalidBookingRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingRequest(InvalidBookingRequestException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.unprocessableEntity().body(response);
    }

    // Hết chỗ hoặc huỷ đơn đã huỷ → 409 Conflict.
    @ExceptionHandler({SoldOutException.class, BookingAlreadyCancelledException.class})
    public ResponseEntity<ErrorResponse> handleBookingConflict(RuntimeException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Giá trị tham số truy vấn không hợp lệ (sort/category lạ) → 400 với message tự viết.
    @ExceptionHandler(InvalidRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(InvalidRequestParameterException exception) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            exception.getMessage(),
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(response);
    }

    // Sai kiểu tham số (vd path variable id không phải số) → 400 với message gọn, không lộ chi tiết Spring/Java.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String type = exception.getRequiredType() != null
            ? exception.getRequiredType().getSimpleName()
            : "the expected type";
        ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Parameter '" + exception.getName() + "' must be of type " + type,
            Map.of(),
            LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(response);
    }
}
