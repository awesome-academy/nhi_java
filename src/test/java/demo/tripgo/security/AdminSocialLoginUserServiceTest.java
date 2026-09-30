package demo.tripgo.security;

import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phần "chỉ admin mới vào được" của đăng nhập Facebook ở khu quản trị. Bước tìm-hoặc-tạo user
// đã có SocialLoginUserServiceTest kiểm, ở đây thay nó bằng mock trả sẵn userId.
class AdminSocialLoginUserServiceTest {

    private static final long USER_ID = 42L;

    private SocialLoginUserService socialLoginUserService;
    private UserRepository users;
    private OAuth2UserRequest request;
    private AdminSocialLoginUserService service;

    @BeforeEach
    void setUp() {
        socialLoginUserService = mock(SocialLoginUserService.class);
        users = mock(UserRepository.class);
        request = mock(OAuth2UserRequest.class);
        service = new AdminSocialLoginUserService(socialLoginUserService, users);

        OAuth2User social = new DefaultOAuth2User(List.of(), Map.of(
            SocialLoginUserService.PRINCIPAL_ATTRIBUTE, "facebook:fb-1",
            SocialLoginUserService.USER_ID_ATTRIBUTE, USER_ID),
            SocialLoginUserService.PRINCIPAL_ATTRIBUTE);
        when(socialLoginUserService.loadUser(request)).thenReturn(social);
    }

    @Test
    void adminLogsInWithEmailAsPrincipalName() {
        stubUser(Role.ADMIN, UserStatus.ACTIVE);

        OAuth2User principal = service.loadUser(request);

        // Tên principal là email, giống form login, để topbar và luật "không tự hạ quyền" dùng chung.
        assertThat(principal.getName()).isEqualTo("nhi@example.com");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    // Người mới vào lần đầu được SocialLoginUserService tạo với role USER -> bị từ chối ở đây,
    // nhưng tài khoản vẫn còn để admin khác nâng quyền.
    @Test
    void normalUserIsDenied() {
        stubUser(Role.USER, UserStatus.ACTIVE);

        assertDenied();
        verify(socialLoginUserService).loadUser(request);
    }

    @Test
    void blockedAdminIsDenied() {
        stubUser(Role.ADMIN, UserStatus.BLOCKED);

        assertDenied();
    }

    private void assertDenied() {
        assertThatThrownBy(() -> service.loadUser(request))
            .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                assertThat(exception.getError().getErrorCode())
                    .isEqualTo(AdminSocialLoginUserService.NOT_ADMIN_ERROR));
    }

    private void stubUser(Role role, UserStatus status) {
        User user = new User();
        user.setEmail("nhi@example.com");
        user.setFullName("Nhi");
        user.setRole(role);
        user.setStatus(status);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }
}
