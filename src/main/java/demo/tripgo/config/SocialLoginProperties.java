package demo.tripgo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// Cấu hình đăng nhập mạng xã hội, đọc từ nhóm social.login trong application.yaml.
@ConfigurationProperties(prefix = "social.login")
public record SocialLoginProperties(
    @DefaultValue Credentials facebook,

    // Có giá trị -> sau khi Facebook xác thực xong, redirect về URL này kèm ?token=<JWT>.
    // Bỏ trống (mặc định) -> trả thẳng JSON { token, user } giống POST /auth/login, để thử được
    // bằng trình duyệt khi chưa có frontend.
    @DefaultValue("") String successRedirectUri
) {
    public record Credentials(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,

        // App Facebook mới chỉ có sẵn public_profile; xin email phải qua App Review.
        @DefaultValue({"public_profile", "email"}) List<String> scopes
    ) {
        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }
}
