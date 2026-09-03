package demo.tripgo.dto.response;

import demo.tripgo.entity.Role;

import java.time.LocalDateTime;

public record UserResponse(
    Long id,
    String fullName,
    String email,
    Role role,
    String status,
    LocalDateTime createdAt
) {
}
