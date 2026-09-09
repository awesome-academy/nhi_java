package demo.tripgo.dto.response;

import java.time.LocalDateTime;

public record ReviewResponse(
    Long id,
    String userFullName,
    int rating,
    String comment,
    LocalDateTime createdAt
) {
}
