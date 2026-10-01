package demo.tripgo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Mặc định Spring redirect về /login?error — TripGo không có trang đó, nên trả JSON đúng contract
// { error: { code, message } } như mọi lỗi khác của API.
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final SecurityErrorResponder securityErrorResponder;

    public OAuth2LoginFailureHandler(SecurityErrorResponder securityErrorResponder) {
        this.securityErrorResponder = securityErrorResponder;
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        securityErrorResponder.write(response, 401,
            "Đăng nhập Facebook thất bại: " + exception.getMessage());
    }
}
