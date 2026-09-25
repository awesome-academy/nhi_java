package demo.tripgo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

// Khu quản trị chạy bằng một SecurityFilterChain RIÊNG, ngược hoàn toàn với chain của API:
//
//                 chain API (SecurityConfig)   chain admin (file này)
//   Phiên         STATELESS                    có session (form login)
//   CSRF          tắt (không dùng cookie)      BẬT (form Thymeleaf tự chèn token)
//   Xác thực      Bearer JWT                   form email + mật khẩu
//   Quyền         authenticated()              hasRole("ADMIN")
//
// securityMatcher giới hạn chain này đúng /admin/**, nên API không bị ảnh hưởng gì.
@Configuration
public class AdminSecurityConfig {

    public static final String ADMIN_BASE = "/admin";
    public static final String LOGIN_PAGE = ADMIN_BASE + "/login";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(ADMIN_BASE + "/**")
            // CSRF bật mặc định: đây là chỗ áp dụng đúng nghĩa, vì có form + session cookie.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(LOGIN_PAGE).permitAll()
                // Cả khu quản trị chỉ dành cho ADMIN; user thường đăng nhập được nhưng vào là 403.
                .anyRequest().hasRole("ADMIN"))
            .formLogin(form -> form
                .loginPage(LOGIN_PAGE)
                .loginProcessingUrl(LOGIN_PAGE)
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl(ADMIN_BASE, true)
                .failureUrl(LOGIN_PAGE + "?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl(ADMIN_BASE + "/logout")
                .logoutSuccessUrl(LOGIN_PAGE + "?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(accessDeniedHandler()));

        return http.build();
    }

    // Hai loại "bị từ chối" rất khác nhau, không được gộp làm một:
    //  - thiếu/sai token CSRF  -> lỗi kỹ thuật, trả 403 để thấy ngay là form hỏng;
    //  - đúng người nhưng sai quyền (user thường vào /admin) -> đưa về trang login kèm lời nhắn.
    // Gộp chung thì lỗi CSRF sẽ hiện thành "không có quyền", che mất nguyên nhân thật.
    private AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandler csrfHandler = new AccessDeniedHandlerImpl();
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                csrfHandler.handle(request, response, exception);
                return;
            }
            response.sendRedirect(request.getContextPath() + LOGIN_PAGE + "?denied");
        };
    }
}
