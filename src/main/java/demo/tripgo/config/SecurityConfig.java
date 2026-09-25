package demo.tripgo.config;

import demo.tripgo.security.JwtAuthenticationFilter;
import demo.tripgo.security.SecurityErrorResponder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static demo.tripgo.config.ApiPathConfig.API_PREFIX;

import java.util.Arrays;
import java.util.List;

// @EnableMethodSecurity bật @PreAuthorize để kiểm quyền ở tầng method (bổ trợ cho URL matcher).
@Configuration
@EnableMethodSecurity
public class SecurityConfig {


    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponder securityErrorResponder;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        SecurityErrorResponder securityErrorResponder
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityErrorResponder = securityErrorResponder;
    }
    // Mã hóa mật khẩu bằng BCrypt trước khi lưu vào database.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS: origin đọc từ cors.allowed-origins (biến môi trường CORS_ALLOWED_ORIGINS, mặc định "*").
    // Dùng Bearer token nên không cần allowCredentials -> "*" hợp lệ.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins:*}") String allowedOrigins
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Chỉ chạy JWT filter trong security chain, không đăng ký thêm ở servlet container.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
            new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    // Cấu hình các quy tắc bảo mật được áp dụng trước khi request đi vào Controller.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            // Tắt CSRF vì ứng dụng cung cấp REST API stateless, không xác thực bằng session/cookie.
            .csrf(AbstractHttpConfigurer::disable)
            // Bật CORS dùng CorsConfigurationSource bên dưới (origin lấy từ biến môi trường).
            .cors(Customizer.withDefaults())
            // 401 (chưa xác thực) và 403 (thiếu quyền) đều trả ErrorResponse như GlobalExceptionHandler,
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    securityErrorResponder.write(
                        response,
                        401,
                        "Vui lòng đăng nhập để tiếp tục"
                    )
                )
                .accessDeniedHandler((request, response, exception) ->
                    securityErrorResponder.write(
                        response,
                        403,
                        "Bạn không có quyền truy cập tài nguyên này"
                    )
                )
            )
            // Không tạo hoặc lưu session đăng nhập trên server; mỗi request phải tự gửi thông tin xác thực.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Cho phép truy cập tài liệu khi bật; profile prod tắt Springdoc trong application-prod.yaml.
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Trang lỗi nội bộ của Spring: khi có exception chưa được GlobalExceptionHandler bắt,
                // request được forward sang /error mà SecurityContext đã mất (filter không chạy lại
                // trên ERROR dispatch). Không permitAll thì client nhận 401 che mất lỗi thật.
                .requestMatchers("/error").permitAll()

                // Tài nguyên tĩnh của khu quản trị (CSS/JS/ảnh). Chúng nằm NGOÀI /admin/** nên
                // rơi vào chain này; không mở thì trang đăng nhập admin sẽ mất hết định dạng.
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                // Ảnh tour do admin tải lên: trang bán hàng công khai phải xem được.
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                // Cho phép người chưa đăng nhập gọi API đăng ký và đăng nhập.
                // Matcher phải gồm cả tiền tố /api/v1: không còn context-path nên servlet
                // container không tách phần này ra nữa (xem ApiPathConfig).
                .requestMatchers(
                    HttpMethod.POST,
                    API_PREFIX + "/auth/register",
                    API_PREFIX + "/auth/login"
                ).permitAll()

                .requestMatchers(HttpMethod.GET, API_PREFIX + "/auth/me").authenticated()

                // Danh sách, chi tiết, ngày khởi hành và đánh giá của tour cho khách xem không cần đăng nhập.
                .requestMatchers(HttpMethod.GET,
                    API_PREFIX + "/tours", API_PREFIX + "/tours/*",
                    API_PREFIX + "/tours/*/availability", API_PREFIX + "/tours/*/reviews").permitAll()

                // Danh sách điểm đến & loại hình tour (cho dropdown lọc).
                .requestMatchers(HttpMethod.GET, API_PREFIX + "/destinations", API_PREFIX + "/categories").permitAll()

                .requestMatchers(HttpMethod.POST, API_PREFIX + "/tours/*/reviews").authenticated()

                .requestMatchers(
                    HttpMethod.POST,
                    API_PREFIX + "/bookings"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    API_PREFIX + "/bookings",
                    API_PREFIX + "/bookings/*"
                ).authenticated()
                .requestMatchers(HttpMethod.PATCH, API_PREFIX + "/bookings/*/cancel").authenticated()

                // Wishlist: toàn bộ đều là thao tác trên dữ liệu riêng của user đang đăng nhập.
                .requestMatchers(HttpMethod.GET, API_PREFIX + "/wishlist").authenticated()
                .requestMatchers(HttpMethod.POST, API_PREFIX + "/wishlist").authenticated()
                .requestMatchers(HttpMethod.DELETE, API_PREFIX + "/wishlist/*").authenticated()

                // Mọi endpoint chưa được liệt kê ở trên đều yêu cầu xác thực.
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
