package demo.tripgo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

// Định dạng lỗi thống nhất theo spec: { "error": { "code", "message" } }.
// Với lỗi validate có thêm "fields" (bản đồ trường -> thông báo); các lỗi khác bỏ qua field này.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ApiError error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
        String code,
        String message,
        Map<String, String> fields
    ) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ApiError(code, message, null));
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ErrorResponse(new ApiError(code, message, fields));
    }
}
