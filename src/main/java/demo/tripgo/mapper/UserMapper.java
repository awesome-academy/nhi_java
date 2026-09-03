package demo.tripgo.mapper;

import demo.tripgo.dto.request.RegisterRequest;
import demo.tripgo.dto.response.RegisterResponse;
import demo.tripgo.dto.response.UserResponse;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    // Chuyển dữ liệu đăng ký từ DTO thành User entity để lưu vào database.
    public User toEntity(RegisterRequest request, String encodedPassword) {
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        // Chỉ lưu mật khẩu đã được PasswordEncoder mã hóa
        user.setPassword(encodedPassword);
        user.setRole(Role.USER);
        return user;
    }

    // Chuyển User entity đã lưu thành response an toàn để trả về client.
    public RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(
            "Registration successful",
            // Chỉ trả thông tin cần thiết; không đưa mật khẩu vào response.
            new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus().name(),
                user.getCreatedAt()
            )
        );
    }
}
