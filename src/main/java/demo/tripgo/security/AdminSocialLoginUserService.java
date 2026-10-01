package demo.tripgo.security;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Đăng nhập Facebook ở trang /admin/login.
//
// Bước tìm-hoặc-tạo user dùng lại nguyên SocialLoginUserService của khách, nên một người dù vào
// từ API hay từ trang quản trị cũng chỉ có MỘT tài khoản. Người lạ được tạo với role USER như mọi
// khách khác; muốn vào khu quản trị thì một admin phải nâng quyền ở trang Người dùng.
//
// Kiểm quyền ngay tại đây (không đợi hasRole("ADMIN") chặn sau): ném lỗi thì Spring không tạo
// phiên đăng nhập nào, còn để lọt qua thì user thường sẽ có một phiên đã xác thực nằm trong khu
// quản trị dù không mở được trang nào.
@Service
public class AdminSocialLoginUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String NOT_ADMIN_ERROR = "admin_access_denied";
    private static final String EMAIL_ATTRIBUTE = "email";

    private final SocialLoginUserService socialLoginUserService;
    private final UserRepository userRepository;

    public AdminSocialLoginUserService(
        SocialLoginUserService socialLoginUserService,
        UserRepository userRepository
    ) {
        this.socialLoginUserService = socialLoginUserService;
        this.userRepository = userRepository;
    }

    // Không @Transactional: tài khoản USER vừa tạo phải được lưu lại kể cả khi bị từ chối, để
    // admin thấy nó trong trang Người dùng mà nâng quyền.
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User social = socialLoginUserService.loadUser(userRequest);

        Object userId = social.getAttributes().get(SocialLoginUserService.USER_ID_ATTRIBUTE);
        if (!(userId instanceof Number id)) {
            throw new IllegalStateException("Thiếu userId trong thuộc tính của phiên OAuth2");
        }
        User user = userRepository.findById(id.longValue())
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy user vừa đăng nhập bằng Facebook"));

        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                NOT_ADMIN_ERROR, "Tài khoản " + user.getEmail() + " không có quyền quản trị", null));
        }

        // Tên principal là email, giống form login: topbar và trang Người dùng (chặn tự hạ quyền)
        // nhận ra admin bằng authentication.getName().
        Map<String, Object> attributes = new LinkedHashMap<>(social.getAttributes());
        attributes.put(EMAIL_ATTRIBUTE, user.getEmail());

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.name())),
            attributes,
            EMAIL_ATTRIBUTE);
    }
}
