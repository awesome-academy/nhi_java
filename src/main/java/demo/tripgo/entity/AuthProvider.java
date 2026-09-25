package demo.tripgo.entity;

import java.util.Locale;

// Nguồn gốc tài khoản: LOCAL là đăng ký bằng email/mật khẩu, còn lại là đăng nhập qua OAuth2.
// Dùng enum thay cờ boolean để thêm Google/X sau này chỉ cần thêm hằng số ở đây.
public enum AuthProvider {
    LOCAL,
    FACEBOOK;

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }

    // registrationId là id khai trong ClientRegistration ("facebook"), Spring Security truyền vào.
    public static AuthProvider fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId không được null");
        }
        return switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "facebook" -> FACEBOOK;
            default -> throw new IllegalArgumentException(
                "Chưa hỗ trợ đăng nhập bằng '" + registrationId + "'");
        };
    }
}
