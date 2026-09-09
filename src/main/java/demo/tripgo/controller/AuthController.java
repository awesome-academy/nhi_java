package demo.tripgo.controller;

import demo.tripgo.dto.request.LoginRequest;
import demo.tripgo.dto.request.RegisterRequest;
import demo.tripgo.dto.response.LoginResponse;
import demo.tripgo.dto.response.RegisterResponse;
import demo.tripgo.service.AuthService;
import demo.tripgo.dto.response.UserResponse;
import demo.tripgo.entity.User;
import demo.tripgo.exception.InvalidCredentialsException;
import demo.tripgo.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        // AuthenticationPrincipalArgumentResolver trả null (không ném lỗi) nếu principal thực tế
        // không phải kiểu User — chặn tại đây để trả 401 rõ ràng thay vì để NPE lọt ra ngoài.
        if (user == null) {
            throw new InvalidCredentialsException("Authentication required");
        }
        return ResponseEntity.ok(userMapper.toUserResponse(user));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }
}
