package demo.tripgo.service;

import demo.tripgo.dto.request.LoginRequest;
import demo.tripgo.dto.request.RegisterRequest;
import demo.tripgo.dto.response.LoginResponse;
import demo.tripgo.dto.response.RegisterResponse;
import demo.tripgo.dto.response.UserResponse;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.exception.EmailAlreadyExistsException;
import demo.tripgo.exception.InvalidCredentialsException;
import demo.tripgo.mapper.UserMapper;
import demo.tripgo.repository.UserRepository;
import demo.tripgo.security.JwtService;
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
    private final JwtService jwtService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        UserMapper userMapper,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    // Xóa khoảng trắng và chuyển email thành chữ thường để kiểm tra nhất quán.
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Đảm bảo toàn bộ quá trình đăng ký chạy trong một transaction.
    // Nếu có lỗi xảy ra, mọi thay đổi database trong hàm này sẽ được rollback.
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        // Email được lưu dạng chữ thường, so sánh trực tiếp để có thể dùng index trên email.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        // Mã hóa mật khẩu gốc bằng BCrypt trước khi đưa vào User entity.
        String encodedPassword = passwordEncoder.encode(request.password());
        User savedUser = userRepository.save(userMapper.toEntity(request, normalizedEmail, encodedPassword));
        // Cấp token ngay để client không phải gọi tiếp /auth/login sau khi đăng ký.
        return userMapper.toRegisterResponse(savedUser, jwtService.generateToken(savedUser));
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() ->
                new InvalidCredentialsException(
                    "Email hoặc mật khẩu không đúng"
                )
            );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new InvalidCredentialsException(
                "Email hoặc mật khẩu không đúng"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException(
                "Tài khoản không ở trạng thái hoạt động"
            );
        }

        String token = jwtService.generateToken(user);

        UserResponse userResponse = userMapper.toUserResponse(user);

        return new LoginResponse(token, userResponse);
    }
}
