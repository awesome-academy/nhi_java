package demo.tripgo.security;

import demo.tripgo.config.AdminSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Đăng nhập Facebook ở khu quản trị thất bại -> quay lại trang đăng nhập với lời nhắn đúng lý do.
//
// Tách hai trường hợp: "không phải admin" dùng chung ?denied với form login (cùng một câu, dù đi
// đường nào); còn lại (user bấm Huỷ bên Facebook, lỗi mạng, state không khớp...) là ?error=facebook,
// không được hiện thành "sai mật khẩu" vì người dùng có nhập mật khẩu nào đâu.
@Component
public class AdminOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminOAuth2LoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        String loginPage = request.getContextPath() + AdminSecurityConfig.LOGIN_PAGE;

        if (exception instanceof OAuth2AuthenticationException oauth2
            && AdminSocialLoginUserService.NOT_ADMIN_ERROR.equals(oauth2.getError().getErrorCode())) {
            log.info("Từ chối đăng nhập Facebook vào khu quản trị: {}", oauth2.getError().getDescription());
            response.sendRedirect(loginPage + "?denied");
            return;
        }

        log.warn("Đăng nhập Facebook vào khu quản trị thất bại: {}", exception.getMessage());
        response.sendRedirect(loginPage + "?error=facebook");
    }
}
