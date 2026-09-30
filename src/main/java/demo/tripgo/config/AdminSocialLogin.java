package demo.tripgo.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

// Phần đăng nhập Facebook mà chain /admin/** gắn thêm vào chính nó.
//
// AdminSecurityConfig luôn tồn tại, còn đăng nhập Facebook chỉ bật khi có client-id. Nên chain
// admin không tự dựng oauth2Login mà hỏi xem có bean này không (SocialLoginConfig tạo ra khi
// tính năng được bật): có thì gắn, không có thì khu quản trị chỉ còn form email/mật khẩu.
@FunctionalInterface
public interface AdminSocialLogin {

    void configure(HttpSecurity http) throws Exception;
}
