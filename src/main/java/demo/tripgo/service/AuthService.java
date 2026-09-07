package demo.tripgo.service;

import demo.tripgo.dto.request.RegisterRequest;
import demo.tripgo.dto.response.RegisterResponse;
import demo.tripgo.entity.User;
import demo.tripgo.exception.EmailAlreadyExistsException;
import demo.tripgo.mapper.UserMapper;
import demo.tripgo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Chuyển đổi giữa RegisterRequest, User entity và RegisterResponse.
    private final UserMapper userMapper;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    // Đảm bảo toàn bộ quá trình đăng ký chạy trong một transaction.
    // Nếu có lỗi xảy ra, mọi thay đổi database trong hàm này sẽ được rollback.
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Xóa khoảng trắng và chuyển email thành chữ thường để kiểm tra nhất quán.
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        // Email được lưu dạng chữ thường, so sánh trực tiếp để có thể dùng index trên email.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        // Mã hóa mật khẩu gốc bằng BCrypt trước khi đưa vào User entity.
        String encodedPassword = passwordEncoder.encode(request.password());
        User savedUser = userRepository.save(userMapper.toEntity(request, normalizedEmail, encodedPassword));
        return userMapper.toRegisterResponse(savedUser);
    }
}
