package demo.tripgo.dto.response;

public record RegisterResponse(
    String message,
    UserResponse user
) {
}
