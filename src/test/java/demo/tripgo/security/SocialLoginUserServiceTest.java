package demo.tripgo.security;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Kiểm phần logic thuần của đăng nhập Facebook (tìm-hoặc-tạo user) mà không gọi ra Graph API:
// delegate được thay bằng một OAuth2UserService trả sẵn attributes đúng như Facebook trả về.
class SocialLoginUserServiceTest {

    private UserRepository users;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        when(users.findByProviderAndProviderId(any(), anyString())).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                ReflectionTestUtils.setField(user, "id", 100L);
            }
            return user;
        });
    }

    @Test
    void createsNewUserFromFacebookAttributes() {
        OAuth2User principal =
            load(Map.of("id", "fb-1", "name", "Nhi Truong", "email", "nhi@example.com"));

        assertThat(principal.getName()).isEqualTo("facebook:fb-1");
        assertThat(principal.<Number>getAttribute(SocialLoginUserService.USER_ID_ATTRIBUTE))
            .isEqualTo(100L);

        User saved = captureSaved();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.FACEBOOK);
        assertThat(saved.getProviderId()).isEqualTo("fb-1");
        assertThat(saved.getEmail()).isEqualTo("nhi@example.com");
        assertThat(saved.getFullName()).isEqualTo("Nhi Truong");
        // Tài khoản Facebook không có mật khẩu -> AuthService.login phải chặn, không so khớp null.
        assertThat(saved.getPassword()).isNull();
    }

    // Không xin được quyền email thì vẫn phải đăng nhập được: cột email là NOT NULL + unique.
    @Test
    void createsUserWithPlaceholderEmailWhenFacebookHidesIt() {
        load(Map.of("id", "fb-2", "name", "Không email"));

        assertThat(captureSaved().getEmail()).isEqualTo("facebook.fb-2@facebook.local");
    }

    // Đã đăng ký bằng email trước đó -> gắn Facebook vào đúng tài khoản cũ (giữ wishlist, đơn hàng).
    // Email Facebook trả về viết hoa, vẫn phải khớp với bản ghi lưu chữ thường.
    @Test
    void linksFacebookToExistingLocalAccountWithSameEmail() {
        User existing = localUser(7L, "nhi@example.com");
        when(users.findByEmail("nhi@example.com")).thenReturn(Optional.of(existing));

        load(Map.of("id", "fb-3", "name", "Nhi", "email", "Nhi@Example.com"));

        User saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.FACEBOOK);
        assertThat(saved.getProviderId()).isEqualTo("fb-3");
    }

    // Email đã thuộc một tài khoản Facebook khác -> dừng, không cướp tài khoản của người ta.
    @Test
    void refusesWhenEmailBelongsToAnotherFacebookAccount() {
        User other = localUser(8L, "nhi@example.com");
        other.setProvider(AuthProvider.FACEBOOK);
        other.setProviderId("fb-khac");
        when(users.findByEmail("nhi@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> load(Map.of("id", "fb-4", "name", "Nhi", "email", "nhi@example.com")))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .hasMessageContaining("đã gắn với một tài khoản Facebook khác");

        verify(users, never()).save(any(User.class));
    }

    // Đăng nhập lần hai: tìm theo providerId, cập nhật tên mới chứ không tạo thêm user.
    @Test
    void secondLoginUpdatesNameInsteadOfCreatingAnotherUser() {
        User existing = localUser(9L, "nhi@example.com");
        existing.setProvider(AuthProvider.FACEBOOK);
        existing.setProviderId("fb-5");
        when(users.findByProviderAndProviderId(AuthProvider.FACEBOOK, "fb-5"))
            .thenReturn(Optional.of(existing));

        load(Map.of("id", "fb-5", "name", "Tên mới", "email", "nhi@example.com"));

        User saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(9L);
        assertThat(saved.getFullName()).isEqualTo("Tên mới");
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void rejectsProviderWithoutUserId() {
        assertThatThrownBy(() -> load(Map.of("name", "Thiếu id")))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .hasMessageContaining("không trả về id người dùng");
    }

    private User captureSaved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    private OAuth2User load(Map<String, Object> attributes) {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = request ->
            new DefaultOAuth2User(List.of(), attributes, attributes.containsKey("id") ? "id" : "name");
        return new SocialLoginUserService(users, delegate).loadUser(userRequest());
    }

    private User localUser(Long id, String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Tên cũ");
        user.setRole(Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("facebook")
            .clientId("id")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/facebook")
            .authorizationUri("https://www.facebook.com/dialog/oauth")
            .tokenUri("https://graph.facebook.com/oauth/access_token")
            .userInfoUri("https://graph.facebook.com/me")
            .userNameAttributeName("id")
            .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, token);
    }
}
