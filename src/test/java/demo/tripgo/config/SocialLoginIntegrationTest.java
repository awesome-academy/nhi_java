package demo.tripgo.config;

import demo.tripgo.entity.AuthProvider;
import demo.tripgo.entity.Role;
import demo.tripgo.entity.User;
import demo.tripgo.entity.UserStatus;
import demo.tripgo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bật đăng nhập Facebook bằng credential giả: đủ để kiểm phần TripGo tự làm (dựng URL uỷ quyền,
// chặn đăng nhập mật khẩu cho tài khoản social). Phần đổi code lấy token là việc của Facebook,
// không test được ở đây và cũng không phải code của mình.
@SpringBootTest(properties = {
    "social.login.facebook.client-id=test-client-id",
    "social.login.facebook.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialLoginIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        users.deleteAll();
    }

    // Vào /oauth2/authorization/facebook -> chuyển hướng sang trang xin quyền của Facebook.
    @Test
    void authorizationEndpointRedirectsToFacebook() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/facebook"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://www.facebook.com/dialog/oauth");
        assertThat(location).contains("client_id=test-client-id");
        // state chống CSRF cho chính luồng OAuth2; code_challenge là PKCE.
        assertThat(location).contains("state=");
        assertThat(location).contains("code_challenge=");
    }

    // Provider lạ -> không dựng được authorization request.
    @Test
    void unknownProviderIsRejected() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is4xxClientError());
    }

    // Tài khoản tạo qua Facebook không có mật khẩu: đăng nhập bằng form phải bị chặn với lời nhắn
    // rõ ràng, KHÔNG được ném lỗi do so khớp mật khẩu với null.
    @Test
    void passwordLoginIsRejectedForFacebookAccount() throws Exception {
        User user = new User();
        user.setEmail("fb@example.com");
        user.setFullName("Người dùng Facebook");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setProvider(AuthProvider.FACEBOOK);
        user.setProviderId("fb-100");
        users.save(user);

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fb@example.com\",\"password\":\"bat-ky-mat-khau-nao\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value(
                "Tài khoản này đăng nhập bằng Facebook, vui lòng dùng đăng nhập Facebook"));
    }

    // Chain OAuth2 chỉ nhận /oauth2/** và /login/oauth2/**; API thường vẫn stateless + 401 JSON.
    @Test
    void apiChainStaysStatelessAndReturnsJsonError() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/wishlist"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value("Vui lòng đăng nhập để tiếp tục"))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // Khu quản trị có chain riêng và đứng trước; bật Facebook không được làm nó đổi hành vi.
    @Test
    void adminChainIsUnaffected() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection());
    }
}
