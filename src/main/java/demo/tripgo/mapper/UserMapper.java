package demo.tripgo.mapper;

import demo.tripgo.dto.request.RegisterRequest;
import demo.tripgo.dto.response.RegisterResponse;
import demo.tripgo.dto.response.UserResponse;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UserMapper {

    // Chuyển dữ liệu đăng ký từ DTO thành User entity để lưu vào database.
    public User toEntity(RegisterRequest request, String normalizedEmail, String encodedPassword) {
        User user = new User();
        user.setFullName(request.name().trim());
        user.setEmail(normalizedEmail);
        // Chỉ lưu mật khẩu đã được PasswordEncoder mã hóa
        user.setPassword(encodedPassword);
        user.setRole(Role.USER);
        return user;
    }

    // Đăng ký thành công trả kèm JWT để client đăng nhập luôn (hợp đồng 6.2).
    public RegisterResponse toRegisterResponse(User user, String token) {
        return new RegisterResponse(token, toUserResponse(user));
    }

    // Chỉ trả thông tin cần thiết; không đưa mật khẩu vào response.
    public UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole().name().toLowerCase(Locale.ROOT)
        );
    }
}
