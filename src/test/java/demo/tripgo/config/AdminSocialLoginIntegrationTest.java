package demo.tripgo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Đăng nhập Facebook ở khu quản trị, bật bằng credential giả. Phần đổi code lấy token là việc
// của Facebook; ở đây kiểm phần TripGo tự nối: URL uỷ quyền, callback riêng của admin, xử lý lỗi,
// và luồng JWT của khách không bị đổi theo.
@SpringBootTest(properties = {
    "social.login.facebook.client-id=test-client-id",
    "social.login.facebook.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSocialLoginIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void loginPageShowsFacebookButton() throws Exception {
        mvc.perform(get("/admin/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/admin/oauth2/authorization/facebook")));
    }

    // Callback phải là của admin: Facebook gọi về đó thì chain /admin/** (có phiên) xử lý.
    @Test
    void adminAuthorizationRedirectsToFacebookWithAdminCallback() throws Exception {
        String location = redirectOf("/admin/oauth2/authorization/facebook");

        assertThat(location).startsWith("https://www.facebook.com/dialog/oauth");
        assertThat(location).contains("client_id=test-client-id");
        assertThat(location).contains("redirect_uri=http://localhost/admin/login/oauth2/code/facebook");
        assertThat(location).contains("state=");
        assertThat(location).contains("code_challenge=");
    }

    @Test
    void customerFlowKeepsItsOwnCallback() throws Exception {
        assertThat(redirectOf("/oauth2/authorization/facebook"))
            .contains("redirect_uri=http://localhost/login/oauth2/code/facebook");
    }

    // Callback không kèm authorization request trong phiên (vd: state giả mạo, phiên đã hết) ->
    // về trang đăng nhập với lời nhắn của Facebook, không phải "sai mật khẩu".
    @Test
    void failedCallbackGoesBackToLoginWithFacebookError() throws Exception {
        mvc.perform(get("/admin/login/oauth2/code/facebook").param("code", "x").param("state", "y"))
            .andExpect(redirectedUrl("/admin/login?error=facebook"));

        mvc.perform(get("/admin/login").param("error", "facebook"))
            .andExpect(content().string(containsString("Đăng nhập Facebook không thành công")));
    }

    // Có thêm oauth2Login nhưng điểm vào khi chưa đăng nhập vẫn phải là /admin/login.
    @Test
    void anonymousStillGoesToAdminLoginPage() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(redirectedUrl("/admin/login"));
    }

    private String redirectOf(String path) throws Exception {
        MvcResult result = mvc.perform(get(path))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return URLDecoder.decode(result.getResponse().getRedirectedUrl(), StandardCharsets.UTF_8);
    }
}
