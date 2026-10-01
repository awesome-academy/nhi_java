package demo.tripgo.security;

import demo.tripgo.entity.AuthProvider;

import java.util.Map;

// Chuẩn hoá dữ liệu user thô của từng nhà cung cấp về một kiểu chung, để SocialLoginUserService
// không phải biết Facebook trả JSON hình dạng ra sao.
public record SocialUserAttributes(
    AuthProvider provider,
    String providerId,
    String fullName,
    String email
) {

    public static SocialUserAttributes of(String registrationId, Map<String, Object> attributes) {
        AuthProvider provider = AuthProvider.fromRegistrationId(registrationId);
        return switch (provider) {
            case FACEBOOK -> fromFacebook(attributes);
            case LOCAL -> throw new IllegalArgumentException("LOCAL không phải nhà cung cấp OAuth2");
        };
    }

    // Graph API trả { id, name, email }; email vắng mặt nếu user không cấp quyền.
    static SocialUserAttributes fromFacebook(Map<String, Object> attributes) {
        return new SocialUserAttributes(
            AuthProvider.FACEBOOK,
            text(attributes.get("id")),
            text(attributes.get("name")),
            text(attributes.get("email")));
    }

    // Tên principal của phiên OAuth2, dạng "facebook:123...".
    public String principalName() {
        return provider.getSlug() + ":" + providerId;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
