package demo.tripgo.security;

import tools.jackson.databind.ObjectMapper;
import demo.tripgo.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

// Ghi lỗi bảo mật (401/403) theo cùng contract ErrorResponse mà GlobalExceptionHandler dùng,
// để client chỉ phải xử lý một format lỗi duy nhất cho toàn bộ API.
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        ErrorResponse body = new ErrorResponse(
            status,
            message,
            Map.of(),
            LocalDateTime.now()
        );
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
