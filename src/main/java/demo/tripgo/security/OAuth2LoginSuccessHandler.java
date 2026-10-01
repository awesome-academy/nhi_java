package demo.tripgo.security;

import demo.tripgo.dto.response.LoginResponse;
import demo.tripgo.entity.User;
import demo.tripgo.mapper.UserMapper;
import demo.tripgo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

// Điểm nối giữa OAuth2 (có phiên) và phần còn lại của API (stateless): Facebook xác thực xong thì
// đổi ngay sang JWT của TripGo, để client dùng chung một loại token cho mọi endpoint.
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final String successRedirectUri;

    public OAuth2LoginSuccessHandler(
        UserRepository userRepository,
        JwtService jwtService,
        UserMapper userMapper,
        ObjectMapper objectMapper,
        @Value("${social.login.success-redirect-uri:}") String successRedirectUri
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.successRedirectUri = successRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        User user = loadUser(authentication);
        String token = jwtService.generateToken(user);

        // Bỏ phiên ngay khi đã có JWT: phiên chỉ tồn tại để giữ state trong lúc bắt tay với Facebook.
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        if (!successRedirectUri.isBlank()) {
            response.sendRedirect(UriComponentsBuilder.fromUriString(successRedirectUri)
                .queryParam("token", token)
                .build()
                .toUriString());
            return;
        }

        // Không cấu hình frontend -> trả đúng body của POST /auth/login để thử thẳng bằng trình duyệt.
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
            new LoginResponse(token, userMapper.toUserResponse(user)));
    }

    private User loadUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OAuth2User principal)) {
            throw new IllegalStateException("Principal sau khi đăng nhập OAuth2 phải là OAuth2User");
        }
        Object userId = principal.getAttributes().get(SocialLoginUserService.USER_ID_ATTRIBUTE);
        if (!(userId instanceof Number id)) {
            throw new IllegalStateException("Thiếu userId trong thuộc tính của phiên OAuth2");
        }
        return userRepository.findById(id.longValue())
            .orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy user vừa đăng nhập bằng Facebook"));
    }
}
