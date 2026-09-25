package demo.tripgo.config;

import demo.tripgo.security.OAuth2LoginFailureHandler;
import demo.tripgo.security.OAuth2LoginSuccessHandler;
import demo.tripgo.security.SocialLoginUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

// Cả cụm đăng nhập Facebook chỉ được tạo khi có FACEBOOK_CLIENT_ID + FACEBOOK_CLIENT_SECRET.
// Thiếu cấu hình thì ứng dụng vẫn chạy bình thường với đăng nhập email/mật khẩu — tiện cho test
// và cho môi trường dev chưa xin được app Facebook.
// (@ConfigurationProperties không đánh giá SpEL nên phải kiểm bằng @ConditionalOnExpression.)
@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
@ConditionalOnExpression("!'${social.login.facebook.client-id:}'.isBlank()")
public class SocialLoginConfig {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginConfig.class);

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(SocialLoginProperties properties) {
        if (!properties.facebook().isConfigured()) {
            throw new IllegalStateException(
                "Đăng nhập Facebook cần CẢ client-id và client-secret. Kiểm tra FACEBOOK_CLIENT_SECRET.");
        }
        log.info("Đã bật đăng nhập Facebook");
        return new InMemoryClientRegistrationRepository(List.of(facebook(properties.facebook())));
    }

    // Chain riêng cho lúc bắt tay với Facebook. Tách khỏi chain API vì OAuth2 buộc phải giữ
    // authorization request + tham số state giữa hai request (đi và callback) — việc mà chain
    // STATELESS của API không làm được.
    //
    // Thứ tự đứng SAU chain /admin/** (HIGHEST_PRECEDENCE) để hai securityMatcher không tranh nhau.
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain oauth2LoginFilterChain(
        HttpSecurity http,
        ClientRegistrationRepository clientRegistrationRepository,
        SocialLoginUserService socialLoginUserService,
        OAuth2LoginSuccessHandler successHandler,
        OAuth2LoginFailureHandler failureHandler
    ) throws Exception {
        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            // Tham số state (và PKCE) đã chống CSRF cho chính luồng OAuth2; không có form nào ở đây.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint ->
                    endpoint.authorizationRequestResolver(pkceResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo.userService(socialLoginUserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler));

        return http.build();
    }

    // Endpoint không ghi phiên bản: Facebook tự định tuyến, không cũ đi theo thời gian.
    private ClientRegistration facebook(SocialLoginProperties.Credentials credentials) {
        return CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
            .clientId(credentials.clientId())
            .clientSecret(credentials.clientSecret())
            .clientName("Facebook")
            .scope(credentials.scopes().toArray(String[]::new))
            .authorizationUri("https://www.facebook.com/dialog/oauth")
            .tokenUri("https://graph.facebook.com/oauth/access_token")
            .userInfoUri("https://graph.facebook.com/me?fields=id,name,email")
            .userNameAttributeName("id")
            .build();
    }

    // PKCE: kể cả khi mã authorization bị lộ trên đường redirect, thiếu code_verifier vẫn không
    // đổi được token.
    //
    // Bọc thêm một lớp nuốt IllegalArgumentException: gõ nhầm /oauth2/authorization/google (provider
    // chưa đăng ký) sẽ ném lỗi và thành trang 500. Trả null thì request đi tiếp và kết thúc bằng
    // 404 — đúng nghĩa "không có đường dẫn này" và không làm log đầy stack trace vô ích.
    private OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository repository) {
        DefaultOAuth2AuthorizationRequestResolver delegate =
            new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return resolveQuietly(() -> delegate.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return resolveQuietly(() -> delegate.resolve(request, clientRegistrationId));
            }

            private OAuth2AuthorizationRequest resolveQuietly(
                java.util.function.Supplier<OAuth2AuthorizationRequest> supplier
            ) {
                try {
                    return supplier.get();
                } catch (IllegalArgumentException exception) {
                    log.debug("Bỏ qua yêu cầu uỷ quyền không hợp lệ: {}", exception.getMessage());
                    return null;
                }
            }
        };
    }
}
