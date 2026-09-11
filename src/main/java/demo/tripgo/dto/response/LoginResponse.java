package demo.tripgo.dto.response;

public record LoginResponse(
    String token,
    UserResponse user
) {
}
