package demo.tripgo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Mã hóa mật khẩu bằng BCrypt trước khi lưu vào database.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cấu hình các quy tắc bảo mật được áp dụng trước khi request đi vào Controller.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // Tắt CSRF vì ứng dụng cung cấp REST API stateless, không xác thực bằng session/cookie.
            .csrf(csrf -> csrf.disable())
            // Không tạo hoặc lưu session đăng nhập trên server; mỗi request phải tự gửi thông tin xác thực.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Cho phép người chưa đăng nhập gọi API đăng ký tài khoản.
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                // Tất cả endpoint còn lại đều yêu cầu người dùng đã được xác thực.
                .anyRequest().authenticated()
            )
            .build();
    }
}
