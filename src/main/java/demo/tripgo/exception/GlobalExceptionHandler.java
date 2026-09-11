package demo.tripgo.exception;

import demo.tripgo.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Trả lỗi theo định dạng thống nhất { "error": { "code", "message", "fields"? } }.
// code = tên HttpStatus (vd "NOT_FOUND"); fields chỉ có ở lỗi validate. Không lộ stack trace/secret.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return build(HttpStatus.CONFLICT, "Dữ liệu bị trùng với bản ghi đã tồn tại");
    }

    @ExceptionHandler({EmailAlreadyExistsException.class, ReviewAlreadyExistsException.class,
        SoldOutException.class, BookingAlreadyCancelledException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    // Hợp đồng 6.2 quy định code riêng INVALID_CREDENTIALS (không dùng tên HttpStatus).
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("INVALID_CREDENTIALS", exception.getMessage()));
    }

    // Tài nguyên có tồn tại nhưng người gọi chưa đủ điều kiện -> 403.
    @ExceptionHandler(ReviewNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleReviewNotAllowed(ReviewNotAllowedException exception) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage());
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

    // Body JSON không parse được (sai kiểu, JSON hỏng). PHẢI có handler ở đây: nếu để exception
    // lọt ra ngoài, Spring forward sang /error, mà OncePerRequestFilter không chạy lại trên ERROR
    // dispatch -> SecurityContext rỗng -> client nhận 401 sai lệch thay vì lỗi thật.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatch) {
            String field = mismatch.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((parent, child) -> parent + "." + child)
                .orElse(null);
            // Sai kiểu ở một trường cụ thể -> báo giống lỗi bean-validation để client xử lý đồng nhất.
            if (field != null) {
                return buildValidation(new LinkedHashMap<>(
                    Map.of(field, field + " " + describeType(mismatch.getTargetType()))));
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Body của request không hợp lệ hoặc không phải JSON đúng định dạng");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST,
            "Tham số '" + exception.getName() + "' " + describeType(exception.getRequiredType()));
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

    // Hợp đồng 6.5 quy định code riêng VALIDATION (không dùng tên HttpStatus).
    private ResponseEntity<ErrorResponse> buildValidation(Map<String, String> fields) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("VALIDATION", "Dữ liệu không hợp lệ", fields));
    }

    // Lỗi ép kiểu khi bind (vd minPrice=abc): thay message nội bộ của Spring bằng "must be of type X".
    private String fieldErrorMessage(FieldError error) {
        if ("typeMismatch".equals(error.getCode())) {
            // Nêu tên trường ngay trong message, đồng bộ với các message tự viết
            // (vd "adults phải ít nhất là 1") để client hiển thị được mà không cần ghép key.
            return error.getField() + " " + describeType(requiredType(error));
        }
        return error.getDefaultMessage();
    }

    private Class<?> requiredType(FieldError error) {
        try {
            return error.unwrap(TypeMismatchException.class).getRequiredType();
        } catch (RuntimeException ignored) {
            // Không lấy được kiểu yêu cầu thì describeType() dùng mô tả chung.
            return null;
        }
    }

    // Dịch kiểu Java sang mô tả cho người dùng cuối. Không lộ tên lớp ("Long", "BigDecimal")
    // vì người nhập liệu không biết đó là gì; họ chỉ cần biết phải nhập dạng nào.
    private String describeType(Class<?> type) {
        if (type == null) {
            return "có giá trị không đúng định dạng";
        }
        if (type == Long.class || type == Integer.class || type == Short.class || type == Byte.class
            || type == long.class || type == int.class) {
            return "phải là số nguyên";
        }
        if (Number.class.isAssignableFrom(type) || type == double.class || type == float.class) {
            return "phải là số";
        }
        if (type == LocalDate.class) {
            return "phải là ngày theo định dạng YYYY-MM-DD";
        }
        if (type == LocalDateTime.class) {
            return "phải là ngày giờ hợp lệ";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "phải là true hoặc false";
        }
        if (type.isEnum()) {
            return "có giá trị không hợp lệ";
        }
        return "có giá trị không đúng định dạng";
    }
}
