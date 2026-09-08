package demo.tripgo.dto.response;

public record LoginResponse(
    String message,
    String accessToken,
    String tokenType,
    UserResponse user
) {
}
