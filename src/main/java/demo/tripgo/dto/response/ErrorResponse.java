package demo.tripgo.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
    int status,
    String message,
    Map<String, String> errors,
    LocalDateTime timestamp
) {
}
