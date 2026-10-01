package demo.tripgo.security;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Chạy sau khi Spring Security đã đổi được access token: lấy thông tin user từ Facebook rồi
// tìm-hoặc-tạo bản ghi tương ứng trong bảng users. OAuth2LoginSuccessHandler dùng userId ở đây
// để phát JWT của TripGo.
@Service
public class SocialLoginUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final UserRepository userRepository;

    // @Autowired tường minh: lớp có hai constructor nên Spring không tự chọn được cái nào.
    @Autowired
    public SocialLoginUserService(UserRepository userRepository) {
        this(userRepository, new DefaultOAuth2UserService());
    }

    // Cho phép thay delegate để unit test không cần gọi thật ra Graph API.
    SocialLoginUserService(
        UserRepository userRepository,
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
    ) {
        this.userRepository = userRepository;
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User raw = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        SocialUserAttributes social;
        try {
            social = SocialUserAttributes.of(registrationId, raw.getAttributes());
        } catch (IllegalArgumentException exception) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", exception.getMessage(), null), exception);
        }
        if (social.providerId() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                "invalid_user_info", "Facebook không trả về id người dùng", null));
        }

        User user = upsert(social);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(PRINCIPAL_ATTRIBUTE, social.principalName());
        attributes.put(USER_ID_ATTRIBUTE, user.getId());
        attributes.put("provider", social.provider().getSlug());
        attributes.put("fullName", user.getFullName());
        attributes.put("email", user.getEmail());

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
            attributes,
            PRINCIPAL_ATTRIBUTE);
    }

    private User upsert(SocialUserAttributes social) {
        User user = userRepository
            .findByProviderAndProviderId(social.provider(), social.providerId())
            .orElseGet(() -> linkOrCreate(social));

        // Facebook là nguồn sự thật cho tên: user đổi tên bên đó thì lần đăng nhập sau cập nhật theo.
        if (social.fullName() != null) {
            user.setFullName(social.fullName());
        }
        return userRepository.save(user);
    }

    // Đã có tài khoản LOCAL trùng email thì gắn Facebook vào chính tài khoản đó, vì cột email là
    // unique — tạo mới sẽ vi phạm ràng buộc, mà user cũng muốn dùng lại đơn hàng/wishlist cũ.
    private User linkOrCreate(SocialUserAttributes social) {
        String email = social.email() == null ? null : social.email().toLowerCase(Locale.ROOT);

        Optional<User> byEmail = email == null
            ? Optional.empty()
            : userRepository.findByEmail(email);

        User user = byEmail
            .map(existing -> claimLocalAccount(existing, email))
            .orElseGet(() -> newUser(social, email));
        user.setProvider(social.provider());
        user.setProviderId(social.providerId());
        return user;
    }

    // Chỉ gắn vào tài khoản LOCAL. Nếu email đã thuộc một tài khoản Facebook khác (user đổi email
    // bên Facebook rồi đăng nhập bằng tài khoản mới) thì dừng, không cướp tài khoản của người khác.
    private User claimLocalAccount(User existing, String email) {
        if (!existing.isLocalAccount()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                "email_already_linked",
                "Email " + email + " đã gắn với một tài khoản Facebook khác", null));
        }
        return existing;
    }

    private User newUser(SocialUserAttributes social, String email) {
        User user = new User();
        // Email là NOT NULL + unique: user không cấp quyền email thì tự sinh một địa chỉ giữ chỗ.
        user.setEmail(email != null
            ? email
            : social.principalName().replace(':', '.') + "@facebook.local");
        user.setFullName(social.fullName() != null ? social.fullName() : "Người dùng Facebook");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
