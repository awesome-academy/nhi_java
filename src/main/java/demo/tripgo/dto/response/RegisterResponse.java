package demo.tripgo.dto.response;

// Đăng ký xong trả luôn JWT để client vào thẳng ứng dụng, không phải gọi tiếp /auth/login.
public record RegisterResponse(
    String token,
    UserResponse user
) {
}
